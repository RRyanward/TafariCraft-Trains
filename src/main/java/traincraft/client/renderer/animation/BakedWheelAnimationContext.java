package traincraft.client.renderer.animation;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import traincraft.Traincraft;

/**
 * TC_DEV38_R1G1Z7B_REMAINING_FLEET_RUNNING_GEAR
 * TC_DEV38_R1G1Z7C_CONSERVATIVE_STATIC_SAFETY
 *
 * Production-candidate animation for the remaining baked/exact fleet.
 *
 * Rules:
 * - never invents geometry;
 * - only transforms authored 24-vertex converter boxes;
 * - finds mirrored wheel-face pairs and keeps them on their authored centers;
 * - steam-only mirrored low side bars can receive crank/rod motion;
 * - steam-only outboard small side bars can receive piston/crosshead stroke;
 * - accepted / intentionally wheel-less stock is excluded completely;
 * - the three r1g1z6a runtime-proven baked sets keep their exact audited box
 *   selections and receive only the requested local polarity reversal.
 */
public final class BakedWheelAnimationContext {
    // TC_DEV38_R1G1Z7D3_LOGGED_TARGETED_CHUNK_DEBUG
    private static final Logger DEEP_LOG = LogUtils.getLogger();

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final Map<String, MeshProfile> CACHE = new HashMap<>();

    // TC_DEV38_R1G1Z7D2_DEEP_WHEEL_UV_SELECTION_DEBUG
    // One line per selected baked chunk for the lifetime of the client.
    // A fresh client launch guarantees a fresh diagnostic set.
    private static final Set<String> DEEP_DEBUG_LOGGED = new HashSet<>();
    private static final Set<String> DEEP_DEBUG_BEGUN = new HashSet<>();

    // TC_DEV38_R1G1Z7E_ALL_CHUNK_INVENTORY
    // Read-only candidate inventory. This never selects or transforms geometry.
    private static final Set<String> DEEP_CANDIDATE_LOGGED = new HashSet<>();

    // TC_DEV38_R1G1Z7G_VISUAL_ISOLATION
    private static final Set<String> ISO_BAKED_LOGGED = new HashSet<>();
    private static final Set<String> DEEP_CANDIDATE_TARGETS = Set.of(
            "locomotive_diesel_chme3",
            "locomotive_diesel_gp7",
            "locomotive_diesel_v60",
            "locomotive_steam_4_4_0",
            "locomotive_steam_adler",
            "locomotive_steam_br01",
            "locomotive_steam_br80",
            "locomotive_steam_forney",
            "locomotive_steam_ussr");

    private static final int BOX_VERTICES = 24;
    private static final float EPS_DIM = 0.006F;
    private static final float EPS_PLANE = 0.020F;

    private static final Set<String> LOCKED_OR_WHEELLESS = Set.of(
            "locomotive_steam_small",
            "freight_cart",
            "passenger_coach_blue",
            "caboose",
            "locomotive_diesel_sd40",
            "locomotive_diesel_sd70",
            "gs4_passenger_car",
            "gs4_tail_car",
            "gs4_baggage_car",
            "gs4_observatory_car",
            "tracks_builder",
            "locomotive_electric_br_e69",
            "locomotive_electric_vl10",
            "locomotive_electric_high_speed_zeroed",
            "locomotive_steam_snow_plow",
            "mob_transport_car");

    private BakedWheelAnimationContext() {}

    public static void begin(Entity entity, float phase) {
        if (entity == null) {
            CURRENT.remove();
            return;
        }

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !Traincraft.MOD_ID.equals(key.getNamespace())) {
            CURRENT.remove();
            return;
        }

        CURRENT.set(new State(key.getPath(), phase, entity.tickCount));

        // Proves the baked context was entered even if no chunk is selected.
        if (DEEP_DEBUG_BEGUN.add(key.getPath())) {
            DEEP_LOG.info(String.format(
                    java.util.Locale.ROOT,
                    "[TC-RG-DEEP-BEGIN] entity=%s phase=%.6f",
                    key.getPath(),
                    phase));
        }
    }

    // TC_DEV38_R1G1Z7P3_440_PAIRED_PANEL_PHASE
    // r1g1z7n focused phase uses the 9px / 0.5625-block main driver radius.
    // The authored truck wheels are 6px diameter, so 4.5/3.0 = 1.5x angle.
    public static float exact440PairedWheelPhase() {
        State state = CURRENT.get();
        if (state == null
                || !"locomotive_steam_4_4_0".equals(state.entityId)) {
            return Float.NaN;
        }
        return state.phase * 1.5F;
    }

    public static TransformSpec spec(
            String meshKey,
            int chunkIndex,
            float[] data,
            int stride) {

        State state = CURRENT.get();
        if (state == null
                || meshKey == null
                || data == null
                || chunkIndex < 0
                || stride < 8
                || LOCKED_OR_WHEELLESS.contains(state.entityId)) {
            return null;
        }

        // TC_DEV38_R1G1Z7D1_TENDER_OUT_OF_SCOPE
        // Tender wheel animation is intentionally not part of the current
        // production wheel pass. Keep every steam_tender* mesh completely
        // static here rather than classifying geometry we do not need.
        if (state.entityId.startsWith("steam_tender")) {
            return null;
        }

        // TC_DEV38_R1G1Z7E_ALL_CHUNK_INVENTORY
        // Log the authored chunk even when the conservative production selector
        // chooses to leave it static. This is diagnostic-only.
        deepCandidateInventory(
                state,
                meshKey,
                chunkIndex,
                data,
                stride);

        
        // TC_DEV38_R1G1Z7J1_CHEREPANOV_REAR_RAIL_STATIC
        // User correction + latest logs:
        // chunks 32/36 are the mirrored thin rear seat-side planes that the
        // PROFILE fallback incorrectly selected as wheels. Keep only these
        // two pieces static. Exact Cherepanov main wheels 2/17/18/19 remain.
        if ("locomotive_steam_cherepanov".equals(state.entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_cherepanov.tcm")
                && (chunkIndex == 32 || chunkIndex == 36)) {
            return null;
        }

        // TC_DEV38_R1G1Z7G_VISUAL_ISOLATION
        // Debug-only one-second candidate-group lift. Production exact mappings
        // remain untouched for the validation locomotives.
        TransformSpec isolation = debugIsolation(state, meshKey, chunkIndex);
        if (isolation != null) return isolation;
// TC_DEV38_R1G1Z7E_RW2_FALSE_POSITIVE_SUPPRESSION
        // Runtime video + d4 dimensions isolate these two smaller rear pieces.
        // Keep the six square wheel faces (57/58, 230/231, 232/233) untouched.
        if ("locomotive_steam_rw_type_2".equals(state.entityId)
                && "legacy:traincraft:legacy_meshes/locomotive_steam_rw_type_2.tcm".equals(meshKey)
                && (chunkIndex == 204 || chunkIndex == 210)) {
            return null;
        }

        // Preserve the exact r1g1z6a runtime-proven authored selections, with
        // the user's requested local direction reversal folded in.
        TransformSpec explicit = explicit(
                state.entityId,
                meshKey,
                chunkIndex,
                state.phase);
        if (explicit != null) {
            deepDebugSelection(
                    "EXPLICIT",
                    state,
                    meshKey,
                    chunkIndex,
                    data,
                    stride,
                    explicit);
            return explicit;
        }

        // DEV44_GROUP1_R3_RODS_ONLY: guarded trial for two Group 1 steam meshes.
        // Existing exact wheel transforms above are untouched. The rod-only
        // path never returns a wheel transform, even if analysis finds wheels.
        boolean group1RodTrial =
                ("locomotive_steam_262t".equals(state.entityId)
                        && meshKey.endsWith("legacy_meshes/locomotive_steam_262t.tcm"))
                || ("locomotive_steam_alice_0_4_0".equals(state.entityId)
                        && meshKey.endsWith("legacy_meshes/locomotive_steam_alice_0_4_0.tcm"));
        if (group1RodTrial) {
            String rodKey = "DEV44_G1_R3_ROD|" + state.entityId + "|" + meshKey
                    + "|" + data.length + "|" + stride;
            MeshProfile rodProfile = CACHE.get(rodKey);
            if (rodProfile == null) {
                rodProfile = analyse(state.entityId, data, stride, true);
                // DEV44 GROUP1 R4 read-only classifier audit, emitted once per cached mesh.
                int rodCount = 0;
                int pistonCount = 0;
                int wheelCount = 0;
                StringBuilder selected = new StringBuilder();
                for (int auditChunk = 0; auditChunk < rodProfile.kind.length; auditChunk++) {
                    Kind auditKind = rodProfile.kind[auditChunk];
                    if (auditKind == Kind.WHEEL) wheelCount++;
                    if (auditKind != Kind.ROD && auditKind != Kind.PISTON) continue;
                    if (auditKind == Kind.ROD) rodCount++; else pistonCount++;
                    Geom bounds = rodProfile.geometry[auditChunk];
                    if (selected.length() < 3500) {
                        if (selected.length() > 0) selected.append(';');
                        selected.append(auditChunk).append(':').append(auditKind)
                                .append('@').append(bounds.cx).append(',').append(bounds.cy)
                                .append(',').append(bounds.cz).append('[')
                                .append(bounds.dx).append(',').append(bounds.dy)
                                .append(',').append(bounds.dz).append(']');
                    }
                }
                DEEP_LOG.info("[TC-DEV44-G1-R4-CLASSIFY] entity={} mesh={} chunks={} wheelCandidates={} rodCandidates={} pistonCandidates={} crank={} selected={}",
                        state.entityId, meshKey, rodProfile.kind.length, wheelCount,
                        rodCount, pistonCount, rodProfile.crank, selected.toString());
                if (CACHE.size() > 512) CACHE.clear();
                CACHE.put(rodKey, rodProfile);
            }
            TransformSpec rodSpec = rodProfile.spec(chunkIndex, state.phase);
            if (rodSpec != null
                    && (rodSpec.kind() == Kind.ROD || rodSpec.kind() == Kind.PISTON)) {
                deepDebugSelection("DEV44-G1-R3-ROD-ONLY", state, meshKey,
                        chunkIndex, data, stride, rodSpec);
                return rodSpec;
            }
        }

        // TC_DEV38_R1G1Z7M2_EXACT_ONLY_GUARDS
        // Full sweep proved no additional safely-separable wheel geometry.
        // Do not let PROFILE fallback transform body/rod/carrier chunks.
        if (("locomotive_steam_adler".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_adler.tcm"))
                || ("locomotive_steam_4_4_0".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_4_4_0.tcm"))
                || ("locomotive_steam_262t".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_262t.tcm"))
                || ("locomotive_steam_alice_0_4_0".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_alice_0_4_0.tcm"))
                || ("locomotive_steam_a4_mallard".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_a4_mallard.tcm"))
                || ("locomotive_steam_berkshire_1225".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_berkshire_1225.tcm"))
                || ("locomotive_steam_berkshire_765".equals(state.entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_berkshire_765.tcm"))) {
            return null;
        }
        String cacheKey = state.entityId
                + "|"
                + meshKey
                + "|"
                + data.length
                + "|"
                + stride;

        MeshProfile profile = CACHE.get(cacheKey);
        if (profile == null) {
            profile = analyse(state.entityId, data, stride, false);
            if (CACHE.size() > 512) CACHE.clear();
            CACHE.put(cacheKey, profile);
        }

        TransformSpec selected =
                profile.spec(chunkIndex, phaseFor(state.entityId, state.phase));
        if (selected != null) {
            deepDebugSelection(
                    "PROFILE",
                    state,
                    meshKey,
                    chunkIndex,
                    data,
                    stride,
                    selected);
        }
        return selected;
    }

    /**
     * DEV38 r1g1z7e2 read-only authored-chunk inventory.
     *
     * Logs every converter-sized 24-vertex chunk for only the remaining
     * wheel-failure targets. Unlike the production selector, this deliberately
     * performs no shape filtering so odd legacy orientation cannot hide the
     * real wheel carrier from the next evidence pass.
     */
    private static TransformSpec debugIsolation(
            State state,
            String meshKey,
            int chunkIndex) {

        if (state == null || meshKey == null || chunkIndex < 0) return null;
        // TC_DEV38_R1G1Z7L2_DISABLE_OLD_ISOLATION
        // The one-command full sweep owns visual isolation for these subjects.
        // Returning null here disables only the older candidate LIFT debug;
        // production exact wheel mappings below remain untouched.
        if ("locomotive_diesel_chme3".equals(state.entityId)
                || "locomotive_diesel_gp7".equals(state.entityId)
                || "locomotive_diesel_v60".equals(state.entityId)
                || "locomotive_steam_4_4_0".equals(state.entityId)
                || "locomotive_steam_adler".equals(state.entityId)
                || "locomotive_steam_br01".equals(state.entityId)
                || "locomotive_steam_forney".equals(state.entityId)
                || "locomotive_steam_ussr".equals(state.entityId)) {
            return null;
        }
        // TC_DEV38_R1G1Z7K2_BR80_ISOLATION_BYPASS
        // Keep the old diagnostic branch in source for later debug cleanup,
        // but make it unreachable so BR80 renders normally and the exact
        // production driver mapping below can execute.
        if ("locomotive_steam_br80".equals(state.entityId)) {
            return null;
        }

        int groupCount;
        boolean member;
        String description;

        if ("locomotive_diesel_gp7".equals(state.entityId)
                && "exact:gp7".equals(meshKey)) {

            groupCount = 5;
            int group = Math.floorMod(state.tickCount / 20, groupCount);

            member = switch (group) {
                case 0 -> chunkIndex == 18 || chunkIndex == 19;
                case 1 -> chunkIndex == 26 || chunkIndex == 27;
                case 2 -> chunkIndex == 28 || chunkIndex == 29;
                case 3 -> chunkIndex == 43 || chunkIndex == 44;
                case 4 -> chunkIndex == 3 || chunkIndex == 4
                        || chunkIndex == 47 || chunkIndex == 48;
                default -> false;
            };

            description = switch (group) {
                case 0 -> "18,19";
                case 1 -> "26,27";
                case 2 -> "28,29";
                case 3 -> "43,44";
                case 4 -> "3,4,47,48";
                default -> "";
            };

            return isolationTransform(state, group, member, description);
        }

if ("locomotive_steam_br80".equals(state.entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_br80.tcm")) {

            groupCount = 5;
            int group = Math.floorMod(state.tickCount / 20, groupCount);

            member = switch (group) {
                case 0 -> chunkIndex == 7 || chunkIndex == 28;
                case 1 -> chunkIndex == 60 || chunkIndex == 61;
                case 2 -> chunkIndex == 6 || chunkIndex == 16;
                case 3 -> chunkIndex == 10 || chunkIndex == 13
                        || chunkIndex == 15 || chunkIndex == 72;
                case 4 -> chunkIndex == 43 || chunkIndex == 44
                        || chunkIndex == 45;
                default -> false;
            };

            description = switch (group) {
                case 0 -> "7,28";
                case 1 -> "60,61";
                case 2 -> "6,16";
                case 3 -> "10,13,15,72";
                case 4 -> "43,44,45";
                default -> "";
            };

            return isolationTransform(state, group, member, description);
        }

        if ("locomotive_steam_adler".equals(state.entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_adler.tcm")) {

            groupCount = 6;
            int group = Math.floorMod(state.tickCount / 20, groupCount);

            member = switch (group) {
                case 0 -> chunkIndex == 252 || chunkIndex == 253;
                case 1 -> chunkIndex == 250 || chunkIndex == 251;
                case 2 -> chunkIndex == 221 || chunkIndex == 222;
                case 3 -> chunkIndex == 245 || chunkIndex == 246;
                case 4 -> chunkIndex == 261 || chunkIndex == 262;
                case 5 -> chunkIndex == 198 || chunkIndex == 199;
                default -> false;
            };

            description = switch (group) {
                case 0 -> "252,253";
                case 1 -> "250,251";
                case 2 -> "221,222";
                case 3 -> "245,246";
                case 4 -> "261,262";
                case 5 -> "198,199";
                default -> "";
            };

            return isolationTransform(state, group, member, description);
        }
        return null;
    }

    private static TransformSpec isolationTransform(
            State state,
            int group,
            boolean member,
            String description) {

        if (!member) return null;

        // First half of each one-second slot is lifted; second half returns
        // naturally because the mesh transform is recalculated every render.
        boolean lifted = Math.floorMod(state.tickCount, 20) < 10;
        if (!lifted) return null;

        String logKey = state.entityId + "|" + group;
        if (ISO_BAKED_LOGGED.add(logKey)) {
            DEEP_LOG.info(String.format(
                    java.util.Locale.ROOT,
                    "[TC-RG-ISO-BAKED] entity=%s tick=%d group=%d chunks=%s mode=LIFT",
                    state.entityId,
                    state.tickCount,
                    group,
                    description));
        }

        return new TransformSpec(
                Kind.NONE,
                0,
                0.0F,
                0.0F,
                0.0F,
                1.0F,
                0.0F,
                0.0F,
                0.75F,
                0.0F);
    }
    private static void deepCandidateInventory(
            State state,
            String meshKey,
            int chunkIndex,
            float[] data,
            int stride) {

        if (state == null
                || meshKey == null
                || data == null
                || stride < 5
                || chunkIndex < 0
                || !DEEP_CANDIDATE_TARGETS.contains(state.entityId)) {
            return;
        }

        String key = state.entityId
                + "|"
                + meshKey
                + "|"
                + chunkIndex
                + "|"
                + stride;

        if (!DEEP_CANDIDATE_LOGGED.add(key)) {
            return;
        }

        int firstVertex = chunkIndex * BOX_VERTICES;
        int start = firstVertex * stride;
        int end = Math.min(data.length, start + BOX_VERTICES * stride);

        if (start < 0 || start >= data.length || end <= start) {
            DEEP_LOG.info(
                    "[TC-RG-CAND] entity="
                            + state.entityId
                            + " mesh="
                            + meshKey
                            + " chunk="
                            + chunkIndex
                            + " ERROR=chunk-out-of-range data="
                            + data.length
                            + " stride="
                            + stride);
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        int vertices = 0;
        for (int i = start; i + 4 < end; i += stride) {
            float x = data[i];
            float y = data[i + 1];
            float z = data[i + 2];
            float u = data[i + 3];
            float v = data[i + 4];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
            vertices++;
        }

        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;

        int thinAxis;
        float thin;
        if (dx <= dy && dx <= dz) {
            thinAxis = 0;
            thin = dx;
        } else if (dy <= dx && dy <= dz) {
            thinAxis = 1;
            thin = dy;
        } else {
            thinAxis = 2;
            thin = dz;
        }

        DEEP_LOG.info(String.format(
                java.util.Locale.ROOT,
                "[TC-RG-CAND] entity=%s mesh=%s chunk=%d "
                        + "bounds=[%.5f..%.5f,%.5f..%.5f,%.5f..%.5f] "
                        + "dims=[%.5f,%.5f,%.5f] thinAxis=%d thin=%.5f "
                        + "uv=[%.5f..%.5f,%.5f..%.5f] vertices=%d stride=%d",
                state.entityId,
                meshKey,
                chunkIndex,
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                dx,
                dy,
                dz,
                thinAxis,
                thin,
                minU,
                maxU,
                minV,
                maxV,
                vertices,
                stride));
    }
    /**
     * DEV38 r1g1z7d2 diagnostic only.
     *
     * Logs the exact currently-selected authored 24-vertex box. XYZ dimensions
     * tell us whether the piece is wheel-like or rod/block-like. UV bounds let
     * us distinguish an actual square wheel face whose round silhouette depends
     * on transparent texture pixels from a genuinely wrong mesh selection.
     *
     * This method does not alter phase, transforms, geometry, or selection.
     */
    private static void deepDebugSelection(
            String selectionSource,
            State state,
            String meshKey,
            int chunkIndex,
            float[] data,
            int stride,
            TransformSpec spec) {

        if (selectionSource == null
                || state == null
                || meshKey == null
                || data == null
                || spec == null
                || stride < 5
                || chunkIndex < 0) {
            return;
        }

        String debugKey = state.entityId
                + "|"
                + selectionSource
                + "|"
                + meshKey
                + "|"
                + chunkIndex
                + "|"
                + spec.kind();

        if (!DEEP_DEBUG_LOGGED.add(debugKey)) {
            return;
        }

        int firstVertex = chunkIndex * BOX_VERTICES;
        int start = firstVertex * stride;
        int end = Math.min(data.length, start + BOX_VERTICES * stride);
        if (start < 0 || start >= data.length || end <= start) {
            DEEP_LOG.info(
                    "[TC-RG-DEEP] entity="
                            + state.entityId
                            + " source="
                            + selectionSource
                            + " mesh="
                            + meshKey
                            + " chunk="
                            + chunkIndex
                            + " ERROR=chunk-out-of-range data="
                            + data.length
                            + " stride="
                            + stride);
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        int vertices = 0;
        for (int i = start; i + 4 < end; i += stride) {
            float x = data[i];
            float y = data[i + 1];
            float z = data[i + 2];
            float u = data[i + 3];
            float v = data[i + 4];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
            vertices++;
        }

        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;

        DEEP_LOG.info(String.format(
                java.util.Locale.ROOT,
                "[TC-RG-DEEP] entity=%s source=%s mesh=%s chunk=%d kind=%s axis=%d "
                        + "bounds=[%.5f..%.5f,%.5f..%.5f,%.5f..%.5f] "
                        + "dims=[%.5f,%.5f,%.5f] "
                        + "uv=[%.5f..%.5f,%.5f..%.5f] "
                        + "pivot=[%.5f,%.5f,%.5f] vertices=%d stride=%d",
                state.entityId,
                selectionSource,
                meshKey,
                chunkIndex,
                spec.kind(),
                spec.axis(),
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                dx,
                dy,
                dz,
                minU,
                maxU,
                minV,
                maxV,
                spec.centerX(),
                spec.centerY(),
                spec.centerZ(),
                vertices,
                stride));
    }
    private static float phaseFor(String entityId, float phase) {
        // r1g1z6a runtime result: these three wheel sets were correct geometry
        // but rotated opposite travel. Local-only correction.
        return switch (entityId) {
            case "locomotive_diesel_shunter",
                 "locomotive_electric_minetrain",
                 "locomotive_steam_vb_shay_2" -> -phase;
            default -> phase;
        };
    }

    private static TransformSpec explicit(
            String entityId,
            String meshKey,
            int chunkIndex,
            float phase) {

        // DEV44_G1_R1_EXACT_WHEEL_FACES — Only geom-verified thin wheel discs.
        // All other authored parts retain the existing production logic.
        if ("locomotive_steam_262t".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_262t.tcm")) {
            return switch (chunkIndex) {
                // Three driving axles, two authored outer faces per axle.
                case 0, 5 -> wheel(2, -2.28125F, 0.40625F,
                        chunkIndex == 0 ? -0.375F : 0.375F, phase);
                case 1, 4 -> wheel(2, -1.65625F, 0.40625F,
                        chunkIndex == 1 ? -0.375F : 0.375F, phase);
                case 2, 3 -> wheel(2, -1.03125F, 0.40625F,
                        chunkIndex == 2 ? -0.375F : 0.375F, phase);
                // Leading/trailing small wheel faces.
                case 6, 7 -> wheel(2, -3.40625F, 0.46875F,
                        chunkIndex == 7 ? -0.375F : 0.375F, phase);
                case 8, 9 -> wheel(2, 0.09375F, 0.46875F,
                        chunkIndex == 9 ? -0.375F : 0.375F, phase);
                default -> null;
            };
        }
        if ("locomotive_steam_alice_0_4_0".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_alice_0_4_0.tcm")) {
            return switch (chunkIndex) {
                case 0, 3 -> wheel(2, -1.78125F, 0.40625F,
                        chunkIndex == 3 ? -0.37563F : 0.37563F, phase);
                case 1, 2 -> wheel(2, -0.90625F, 0.40625F,
                        chunkIndex == 2 ? -0.37563F : 0.37563F, phase);
                default -> null;
            };
        }

        // DEV44_G1_R2_EXACT_FACES: only confirmed thin wheel face chunks.
        // A4 Mallard: three drivers + two small leading and one trailing axles.
        if ("locomotive_steam_a4_mallard".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_a4_mallard.tcm")) {
            return switch (chunkIndex) {
                case 0, 7 -> wheel(2, -0.78125F, 0.15625F,
                        chunkIndex == 0 ? 0.37578F : -0.37547F, phase);
                case 1, 8 -> wheel(2, 0.46875F, 0.15625F,
                        chunkIndex == 1 ? 0.37578F : -0.37547F, phase);
                case 2, 9 -> wheel(2, 1.71875F, 0.15625F,
                        chunkIndex == 2 ? 0.37578F : -0.37547F, phase);
                case 3, 6 -> wheel(2, -2.59375F, 0.40625F,
                        chunkIndex == 3 ? 0.37578F : -0.37547F, phase);
                case 4, 5 -> wheel(2, -1.71875F, 0.40625F,
                        chunkIndex == 4 ? 0.37578F : -0.37547F, phase);
                case 10, 11 -> wheel(2, 3.34375F, 0.40625F,
                        chunkIndex == 10 ? 0.37453F : -0.37422F, phase);
                default -> null;
            };
        }
        // The two Berkshire meshes share the same grounded 4-driver and
        // 3-small-axle thin-face geometry. Rod/body geometry is left untouched.
        if (("locomotive_steam_berkshire_1225".equals(entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_berkshire_1225.tcm"))
                || ("locomotive_steam_berkshire_765".equals(entityId)
                    && meshKey.endsWith("legacy_meshes/locomotive_steam_berkshire_765.tcm"))) {
            return switch (chunkIndex) {
                case 10, 17 -> wheel(2, 1.9375F, -0.5625F,
                        chunkIndex == 10 ? 0.31313F : -0.43813F, phase);
                case 11, 16 -> wheel(2, 1.125F, -0.5625F,
                        chunkIndex == 11 ? 0.31313F : -0.43813F, phase);
                case 12, 15 -> wheel(2, 0.3125F, -0.5625F,
                        chunkIndex == 12 ? 0.31313F : -0.43813F, phase);
                case 13, 14 -> wheel(2, -0.5F, -0.5625F,
                        chunkIndex == 13 ? 0.31313F : -0.43813F, phase);
                case 22, 25 -> wheel(2, -1.5F, -0.375F,
                        chunkIndex == 25 ? 0.31313F : -0.43813F, phase);
                case 23, 24 -> wheel(2, -2.34375F, -0.375F,
                        chunkIndex == 24 ? 0.31313F : -0.43813F, phase);
                case 26, 27 -> wheel(2, 3.1875F, -0.375F,
                        chunkIndex == 26 ? 0.31313F : -0.43813F, phase);
                default -> null;
            };
        }

        if ("locomotive_diesel_shunter".equals(entityId)
                && "exact:shunter".equals(meshKey)) {
            float a = -phase * 0.736F;
            return switch (chunkIndex) {
                case 0  -> wheel(2,  0.813F, 0.313F, 0.000F, a);
                case 2  -> wheel(2, -0.813F, 0.313F, 0.000F, a);
                case 35 -> wheel(2,  0.000F, 0.313F, 0.000F, a);
                case 51 -> wheel(2,  0.813F, 0.313F, 0.000F, a);
                case 52 -> wheel(2,  0.000F, 0.313F, 0.000F, a);
                case 53 -> wheel(2, -0.813F, 0.313F, 0.000F, a);
                default -> null;
            };
        }

        if ("locomotive_electric_minetrain".equals(entityId)
                && "exact:minetrain".equals(meshKey)) {
            float a = -phase * 0.96F;
            return switch (chunkIndex) {
                case 2  -> wheel(2,  0.250F, 0.188F, 0.000F, a);
                case 9  -> wheel(2, -0.313F, 0.188F, 0.000F, a);
                case 10 -> wheel(2, -0.313F, 0.188F, 0.000F, a);
                case 11 -> wheel(2,  0.250F, 0.188F, 0.000F, a);
                default -> null;
            };
        }

        if ("locomotive_steam_vb_shay_2".equals(entityId)
                && meshKey.endsWith(
                        "legacy_meshes/locomotive_steam_vb_shay_2.tcm")) {
            float a = -phase * 1.143617F;
            return switch (chunkIndex) {
                case 0  -> wheel(2, -1.313F, 0.875F, 0.313F, a);
                case 2  -> wheel(2, -0.750F, 0.875F, 0.313F, a);
                case 19 -> wheel(2,  0.750F, 0.875F,-0.313F, a);
                case 20 -> wheel(2,  1.313F, 0.875F,-0.313F, a);
                case 21 -> wheel(2,  1.313F, 0.875F, 0.313F, a);
                case 22 -> wheel(2,  0.750F, 0.875F, 0.313F, a);
                case 30 -> wheel(2, -0.750F, 0.875F,-0.313F, a);
                case 32 -> wheel(2, -1.313F, 0.875F,-0.313F, a);
                default -> null;
            };
        }


        // TC_DEV38_R1G1Z7F_EXACT_U57
        // r1g1z7e2 [TC-RG-CAND]: five authored axles, both side faces.
        if ("locomotive_steam_u57".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_u57.tcm")) {
            return switch (chunkIndex) {
                case 46 -> wheel(0,  0.40625F, 0.37500F, 0.43750F, phase);
                case 57 -> wheel(0, -0.40625F, 0.37500F, 0.43750F, phase);
                case 0  -> wheel(0,  0.40625F, 0.37500F, 1.12500F, phase);
                case 1  -> wheel(0, -0.40625F, 0.37500F, 1.12500F, phase);
                case 60 -> wheel(0,  0.40625F, 0.37500F, 1.81250F, phase);
                case 2  -> wheel(0, -0.40625F, 0.37500F, 1.81250F, phase);
                case 61 -> wheel(0,  0.40625F, 0.37500F, 2.50000F, phase);
                case 13 -> wheel(0, -0.40625F, 0.37500F, 2.50000F, phase);
                case 62 -> wheel(0,  0.40625F, 0.37500F, 3.18750F, phase);
                case 24 -> wheel(0, -0.40625F, 0.37500F, 3.18750F, phase);
                default -> null;
            };
        }

        // TC_DEV38_R1G1Z7F_EXACT_KING
        // Three 1.0006-square driver axles + two 0.4381-square leading axles.
        if ("locomotive_steam_king_class".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_king_class.tcm")) {
            return switch (chunkIndex) {
                case 118 -> wheel(2, -0.34375F, 0.12500F, -0.37500F, phase);
                case 119 -> wheel(2, -0.34375F, 0.12500F,  0.37500F, phase);
                case 120 -> wheel(2,  0.90625F, 0.12500F,  0.37500F, phase);
                case 121 -> wheel(2,  0.90625F, 0.12500F, -0.37500F, phase);
                case 122 -> wheel(2, -1.59375F, 0.12500F, -0.37500F, phase);
                case 123 -> wheel(2, -1.59375F, 0.12500F,  0.37500F, phase);
                case 124 -> wheel(2, -2.40625F, 0.40625F, -0.37500F, phase);
                case 125 -> wheel(2, -2.40625F, 0.40625F,  0.37500F, phase);
                case 126 -> wheel(2, -3.59375F, 0.40625F, -0.37500F, phase);
                case 127 -> wheel(2, -3.59375F, 0.40625F,  0.37500F, phase);
                default -> null;
            };
        }

        // TC_DEV38_R1G1Z7F_EXACT_CHEREPANOV
        // Two exact main-driver axles. The smaller 32/36 pair remains static
        // until separately proven.
        if ("locomotive_steam_cherepanov".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_cherepanov.tcm")) {
            return switch (chunkIndex) {
                case 2  -> wheel(2,  0.62500F, 0.31250F, -0.34375F, phase);
                case 18 -> wheel(2,  0.62500F, 0.31250F,  0.34375F, phase);
                case 17 -> wheel(2, -0.18750F, 0.31250F, -0.34375F, phase);
                case 19 -> wheel(2, -0.18750F, 0.31250F,  0.34375F, phase);
                default -> null;
            };
        }

        // TC_DEV38_R1G1Z7F_EXACT_440
        // Two exact main-driver axles. Compound leading-wheel geometry stays
        // static until its authored carrier is proven.
        if ("locomotive_steam_4_4_0".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_4_4_0.tcm")) {
            return switch (chunkIndex) {
                case 20 -> wheel(0, -0.34375F, 0.28125F, -0.09375F, phase);
                case 25 -> wheel(0,  0.34375F, 0.28125F, -0.09375F, phase);
                case 21 -> wheel(0, -0.34375F, 0.28125F,  0.53125F, phase);
                case 24 -> wheel(0,  0.34375F, 0.28125F,  0.53125F, phase);
                default -> null;
            };
        }
        // TC_DEV38_R1G1Z7K2_EXACT_BR80_DRIVERS
        // Latest visual-isolation + authored geometry:
        // three low equal driver solids, centered at the three BR80 axles.
        // Train length=X, wheel axle=Z. Rotate only these authored chunks.
        if ("locomotive_steam_br80".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_br80.tcm")) {
            return switch (chunkIndex) {
                case 43 -> wheel(2, -0.50000F, 0.25000F, 0.00000F, phase);
                case 44 -> wheel(2,  0.75000F, 0.25000F, 0.00000F, phase);
                case 45 -> wheel(2,  0.12500F, 0.25000F, 0.00000F, phase);
                default -> null;
            };
        }

        // TC_DEV38_R1G1Z7M2_EXACT_ADLER_WHEELS
        // Full all-chunk sweep: six authored wheel assemblies.
        // Train length=Z, axle=X, so every wheel group rotates about axis 0.
        if ("locomotive_steam_adler".equals(entityId)
                && meshKey.endsWith("legacy_meshes/locomotive_steam_adler.tcm")) {
            return switch (chunkIndex) {
                // Main wheel - left.
                case 0,1,2,3,4,5,6,7,8,9,10,
                     14,15,16,17,18,19,20,21,22,23,24,25,26,63 ->
                        wheel(0, -0.29400F, 1.09400F, -0.03100F, phase);

                // Main wheel - right.
                case 80,81,82,83,84,85,86,87,88,89,90,91,92,
                     93,94,95,96,97,98,99,100,101,102,103,104 ->
                        wheel(0,  0.29400F, 1.09400F, -0.03100F, phase);

                // Small +Z wheel - left.
                case 27,28,29,30,31,32,33,34,35,
                     45,47,49,50,53,54,57,58 ->
                        wheel(0, -0.29400F, 1.25000F,  0.68800F, phase);

                // Small -Z wheel - left.
                case 36,37,38,39,40,41,42,43,44,
                     46,48,51,52,55,56,59,60 ->
                        wheel(0, -0.29400F, 1.25000F, -0.75000F, phase);

                // Small +Z wheel - right.
                case 105,106,107,108,109,110,111,112,113,
                     114,115,116,117,118,119,120,121 ->
                        wheel(0,  0.29400F, 1.25000F,  0.68800F, phase);

                // Small -Z wheel - right.
                case 122,123,124,125,126,127,128,129,130,
                     131,132,133,134,135,136,137,138 ->
                        wheel(0,  0.29400F, 1.25000F, -0.75000F, phase);

                default -> null;
            };
        }
        return null;
    }

    private static MeshProfile analyse(
            String entityId,
            float[] data,
            int stride,
            boolean enableRodOnly) {

        int chunkFloats = BOX_VERTICES * stride;
        int chunkCount = data.length / chunkFloats;
        if (chunkCount <= 0) return MeshProfile.EMPTY;

        Geom[] g = new Geom[chunkCount];
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            g[chunk] = geometry(data, stride, chunk);
        }

        boolean[] pairedWheel = new boolean[chunkCount];
        int[] wheelAxis = new int[chunkCount];

        for (int i = 0; i < chunkCount; i++) {
            if (!wheelLike(g[i])) continue;

            int axis = thinAxis(g[i]);
            for (int j = i + 1; j < chunkCount; j++) {
                if (!wheelLike(g[j])) continue;
                if (axis != thinAxis(g[j])) continue;
                if (!sameDimensions(g[i], g[j])) continue;
                if (!mirrored(g[i], g[j], axis)) continue;

                pairedWheel[i] = true;
                pairedWheel[j] = true;
                wheelAxis[i] = axis;
                wheelAxis[j] = axis;
                break;
            }
        }

        int dominantAxis = -1;
        int axis0 = 0;
        int axis2 = 0;
        for (int i = 0; i < chunkCount; i++) {
            if (!pairedWheel[i]) continue;
            if (wheelAxis[i] == 0) axis0++;
            if (wheelAxis[i] == 2) axis2++;
        }
        if (axis0 > 0 || axis2 > 0) {
            dominantAxis = axis0 > axis2 ? 0 : 2;
        }

        if (dominantAxis < 0) return MeshProfile.EMPTY;

        // Pick the strongest vertical running-gear band. This rejects round
        // boiler/cab details that happen to be authored as thin square plates.
        float bestY = 0.0F;
        int bestCount = 0;
        float bestDiameter = -1.0F;

        for (int i = 0; i < chunkCount; i++) {
            if (!pairedWheel[i] || wheelAxis[i] != dominantAxis) continue;

            int count = 0;
            float diameterSum = 0.0F;

            for (int j = 0; j < chunkCount; j++) {
                if (!pairedWheel[j] || wheelAxis[j] != dominantAxis) continue;
                if (Math.abs(g[j].cy - g[i].cy) <= 0.22F) {
                    count++;
                    diameterSum += visualDiameter(g[j], dominantAxis);
                }
            }

            float averageDiameter = count > 0 ? diameterSum / count : 0.0F;
            if (count > bestCount
                    || (count == bestCount && averageDiameter > bestDiameter)) {
                bestCount = count;
                bestDiameter = averageDiameter;
                bestY = g[i].cy;
            }
        }

        Kind[] kind = new Kind[chunkCount];
        int[] axis = new int[chunkCount];
        for (int i = 0; i < chunkCount; i++) kind[i] = Kind.NONE;

        float minLong = Float.POSITIVE_INFINITY;
        float maxLong = Float.NEGATIVE_INFINITY;
        float radiusSum = 0.0F;
        int wheelCount = 0;

        for (int i = 0; i < chunkCount; i++) {
            if (!pairedWheel[i]
                    || wheelAxis[i] != dominantAxis
                    || Math.abs(g[i].cy - bestY) > 0.25F) {
                continue;
            }

            kind[i] = Kind.WHEEL;
            axis[i] = dominantAxis;
            wheelCount++;

            float longitudinal = dominantAxis == 2 ? g[i].cx : g[i].cz;
            minLong = Math.min(minLong, longitudinal);
            maxLong = Math.max(maxLong, longitudinal);
            radiusSum += visualDiameter(g[i], dominantAxis) * 0.5F;
        }

        if (wheelCount < 2) return MeshProfile.EMPTY;

        float wheelRadius = radiusSum / wheelCount;
        float crank = clamp(wheelRadius * 0.28F, 0.035F, 0.115F);

        // Steam rods / piston motion. Tenders and ordinary cars never enter
        // this block even if a frame member happens to look rod-like.
        if (enableRodOnly && entityId.startsWith("locomotive_steam_")) {
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < chunkCount; i++) {
                if (kind[i] != Kind.NONE || used.contains(i)) continue;

                int candidateAxis = thinAxis(g[i]);
                if (candidateAxis != dominantAxis
                        || thinDimension(g[i], candidateAxis) > 0.075F
                        || Math.abs(g[i].cy - bestY) > 0.42F) {
                    continue;
                }

                int peer = -1;
                for (int j = i + 1; j < chunkCount; j++) {
                    if (kind[j] != Kind.NONE || used.contains(j)) continue;
                    if (thinAxis(g[j]) != dominantAxis) continue;
                    if (!sameDimensions(g[i], g[j])) continue;
                    if (!mirrored(g[i], g[j], dominantAxis)) continue;
                    peer = j;
                    break;
                }
                if (peer < 0) continue;

                float longDim = dominantAxis == 2 ? g[i].dx : g[i].dz;
                float verticalDim = g[i].dy;
                float aspect = longDim / Math.max(0.001F, verticalDim);
                float longitudinal = dominantAxis == 2 ? g[i].cx : g[i].cz;

                if (longDim >= 0.55F
                        && verticalDim <= 0.34F
                        && aspect >= 2.0F) {
                    kind[i] = Kind.ROD;
                    kind[peer] = Kind.ROD;
                    axis[i] = dominantAxis;
                    axis[peer] = dominantAxis;
                    used.add(i);
                    used.add(peer);
                    continue;
                }

                boolean outsideDrivers =
                        longitudinal < minLong - 0.08F
                        || longitudinal > maxLong + 0.08F;
                boolean nearDrivers =
                        longitudinal > minLong - 2.0F
                        && longitudinal < maxLong + 2.0F;

                if (longDim >= 0.18F
                        && longDim <= 0.90F
                        && verticalDim <= 0.22F
                        && aspect >= 2.0F
                        && outsideDrivers
                        && nearDrivers
                        && Math.abs(g[i].cy - bestY) <= 0.50F) {
                    kind[i] = Kind.PISTON;
                    kind[peer] = Kind.PISTON;
                    axis[i] = dominantAxis;
                    axis[peer] = dominantAxis;
                    used.add(i);
                    used.add(peer);
                }
            }
        }

        return new MeshProfile(g, kind, axis, crank);
    }

    private static Geom geometry(float[] data, int stride, int chunkIndex) {
        int start = chunkIndex * BOX_VERTICES * stride;
        int end = Math.min(data.length, start + BOX_VERTICES * stride);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = start; i + 2 < end; i += stride) {
            float x = data[i];
            float y = data[i + 1];
            float z = data[i + 2];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (!Float.isFinite(minX)) {
            return new Geom(0, 0, 0, 0, 0, 0);
        }

        return new Geom(
                maxX - minX,
                maxY - minY,
                maxZ - minZ,
                (minX + maxX) * 0.5F,
                (minY + maxY) * 0.5F,
                (minZ + maxZ) * 0.5F);
    }

    private static boolean wheelLike(Geom g) {
        int axis = thinAxis(g);
        if (axis == 1) return false;

        float thin = thinDimension(g, axis);
        float a;
        float b;

        if (axis == 0) {
            a = g.dy;
            b = g.dz;
        } else {
            a = g.dx;
            b = g.dy;
        }

        float lo = Math.min(a, b);
        float hi = Math.max(a, b);

        return thin <= 0.005F
                && lo >= 0.180F
                && hi <= 1.300F
                && hi / Math.max(0.001F, lo) <= 1.18F;
    }

    private static int thinAxis(Geom g) {
        if (g.dx <= g.dy && g.dx <= g.dz) return 0;
        if (g.dy <= g.dx && g.dy <= g.dz) return 1;
        return 2;
    }

    private static float thinDimension(Geom g, int axis) {
        return switch (axis) {
            case 0 -> g.dx;
            case 1 -> g.dy;
            default -> g.dz;
        };
    }

    private static boolean sameDimensions(Geom a, Geom b) {
        return Math.abs(a.dx - b.dx) <= EPS_DIM
                && Math.abs(a.dy - b.dy) <= EPS_DIM
                && Math.abs(a.dz - b.dz) <= EPS_DIM;
    }

    private static boolean mirrored(Geom a, Geom b, int axis) {
        float separation;
        float planeA;
        float planeB;

        if (axis == 0) {
            separation = Math.abs(a.cx - b.cx);
            planeA = Math.max(
                    Math.abs(a.cy - b.cy),
                    Math.abs(a.cz - b.cz));
        } else if (axis == 2) {
            separation = Math.abs(a.cz - b.cz);
            planeA = Math.max(
                    Math.abs(a.cx - b.cx),
                    Math.abs(a.cy - b.cy));
        } else {
            separation = Math.abs(a.cy - b.cy);
            planeA = Math.max(
                    Math.abs(a.cx - b.cx),
                    Math.abs(a.cz - b.cz));
        }

        planeB = planeA;
        return separation >= 0.22F && planeB <= EPS_PLANE;
    }

    private static float visualDiameter(Geom g, int axis) {
        if (axis == 0) return (g.dy + g.dz) * 0.5F;
        if (axis == 2) return (g.dx + g.dy) * 0.5F;
        return (g.dx + g.dz) * 0.5F;
    }

    private static TransformSpec wheel(
            int axis,
            float cx,
            float cy,
            float cz,
            float angle) {
        return new TransformSpec(
                Kind.WHEEL,
                axis,
                cx,
                cy,
                cz,
                (float) Math.cos(angle),
                (float) Math.sin(angle),
                0.0F,
                0.0F,
                0.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Kind {
        NONE,
        WHEEL,
        ROD,
        PISTON
    }

    private record State(String entityId, float phase, int tickCount) {}

    private record Geom(
            float dx,
            float dy,
            float dz,
            float cx,
            float cy,
            float cz) {}

    private static final class MeshProfile {
        static final MeshProfile EMPTY =
                new MeshProfile(new Geom[0], new Kind[0], new int[0], 0.0F);

        private final Geom[] geometry;
        private final Kind[] kind;
        private final int[] axis;
        private final float crank;

        MeshProfile(
                Geom[] geometry,
                Kind[] kind,
                int[] axis,
                float crank) {
            this.geometry = geometry;
            this.kind = kind;
            this.axis = axis;
            this.crank = crank;
        }

        TransformSpec spec(int chunkIndex, float phase) {
            if (chunkIndex < 0
                    || chunkIndex >= kind.length
                    || kind[chunkIndex] == Kind.NONE) {
                return null;
            }

            Geom g = geometry[chunkIndex];
            Kind k = kind[chunkIndex];
            int a = axis[chunkIndex];

            if (k == Kind.WHEEL) {
                return wheel(a, g.cx, g.cy, g.cz, phase);
            }

            float cosine = (float) Math.cos(phase);
            float sine = (float) Math.sin(phase);

            if (k == Kind.ROD) {
                float along = crank * (cosine - 1.0F);
                float vertical = crank * sine;

                if (a == 2) {
                    return new TransformSpec(
                            k, a, g.cx, g.cy, g.cz,
                            1.0F, 0.0F,
                            along, vertical, 0.0F);
                }

                return new TransformSpec(
                        k, a, g.cx, g.cy, g.cz,
                        1.0F, 0.0F,
                        0.0F, vertical, along);
            }

            if (k == Kind.PISTON) {
                float stroke = crank * 1.35F * (cosine - 1.0F);

                if (a == 2) {
                    return new TransformSpec(
                            k, a, g.cx, g.cy, g.cz,
                            1.0F, 0.0F,
                            stroke, 0.0F, 0.0F);
                }

                return new TransformSpec(
                        k, a, g.cx, g.cy, g.cz,
                        1.0F, 0.0F,
                        0.0F, 0.0F, stroke);
            }

            return null;
        }
    }

    /**
     * axis: 0=X, 1=Y, 2=Z. Wheels rotate around axis through center.
     * Rod/piston specs use translation only.
     */
    public record TransformSpec(
            Kind kind,
            int axis,
            float centerX,
            float centerY,
            float centerZ,
            float cos,
            float sin,
            float translateX,
            float translateY,
            float translateZ) {

        public boolean rotates() {
            return kind == Kind.WHEEL;
        }
    }
}
