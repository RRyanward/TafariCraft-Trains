package traincraft.client.renderer.animation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * TC_DEV38_R1G1Z7Y16_AUTHORED_PHASE_ZERO_OUTLIER_MASK
 * TC_DEV38_R1G1Z7Y17_LIVE_TUNE_SOURCE_GATED
 * TC_DEV38_R1G1Z7Y18_GUARDED_SLIDER_CRANK_GEOMETRY
 * TC_DEV38_R1G1Z7Y18A_AUTHORED_NEG4_REST_SLIDER_CRANK
 * TC_DEV38_R1G1Z7Y18B_HEAVY_READ_ONLY_ROD_TIP_AXLE_CANDIDATES
 * TC_DEV38_R1G1Z7Y18I_NONHEAVY_LONG_AXIS_RECIPROCATION
 * TC_DEV38_R1G1Z7Y18D_HEAVY_REST_PIN_SLIDER_TRIAL
 * TC_DEV38_R1G1Z7Y18E_READ_ONLY_AXLE_BASIS_PIN_REFERENCE
 * TC_DEV38_R1G1Z7Y18K_MOGUL_FORNEY_GEOMETRY_WHEEL_CRANK_REPAIR
 * TC_DEV38_R1G1Z7Y18L_MOGUL_FORNEY_BREAK_APART_DIAGNOSTIC
 * TC_DEV38_R1G1Z7Y18M_BREAK_APART_GROUNDED_CENTER_PIVOT_REPAIR
 * TC_DEV38_R1G1Z7Y18N_CUSTOM_MOGUL_FORNEY_RUNNING_GEAR
 *
 * y14 runtime proved the direct delegate hook executes, but the DEV42 authored
 * part names live in ModelPart child maps rather than Java fields.
 *
 * y16 retains authored part names and recursively discovered model tree.
 * Its orbital displacement is zero at phase=0, which preserves the exact
 * authored rest pose rather than adding a spurious full radius displacement.
 * DEV42 outliers are temporarily masked for visual acceptance; no geometry
 * or wheel source is modified.
 */
public final class FleetRodPistonPartAnimator {
    private static final org.slf4j.Logger LOG =
            com.mojang.logging.LogUtils.getLogger();

    private static final Set<String> TARGETS = Set.of(
            "locomotive_steam_heavy",
            "locomotive_steam_mogul",
            "locomotive_steam_forney",
            "locomotive_steam_br01",
            "locomotive_steam_ussr",
            "locomotive_diesel_v60");

    private static final Map<Object, Rig> CACHE = new IdentityHashMap<>();
    private static final Set<String> TREE_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> MOTION_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<String, java.util.concurrent.atomic.AtomicInteger>
            FRAME_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();

    // DEV38 y18k: exact runtime geometry selection for the two axle-X steam rigs.
    // The y18j video proved that the old authored-name mapping selected the wrong
    // visible pieces, and Forney's driver wheels were not rotating.  These caches
    // are keyed by actual ModelPart identity and are captured at the batch REST
    // pose before any y18k write.
    private static final Map<ModelPart, Y18KPose> Y18K_BASES =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> Y18K_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Y18KPose(float x, float y, float z,
                            float xRot, float yRot, float zRot) {}
    private record Y18KDims(float dx, float dy, float dz) {}

    // DEV38 y18l: temporary VISUAL-IDENTIFICATION ONLY pass for Mogul/Forney.
    // No wheel/rod physics is inferred here. The pass restores candidate geometry
    // to its captured rest pose, then lifts one exact authored geometry family at
    // a time so video timestamps can be mapped back to concrete ModelPart names.
    private static final long Y18L_SLOT_NANOS = 2_250_000_000L;
    private static final int Y18L_SLOT_COUNT = 8;
    private static final Map<Object, Long> Y18L_START_NANOS =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, Integer> Y18L_LAST_SLOT =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    // DEV38 y18m: one-time evidence log for the break-apart-grounded repair.
    private static final Set<String> Y18M_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // DEV38 y18n: retire the visually ineffective legacy Mogul/Forney linkage
    // pieces and attach new baked ModelPart children to the live recovered model
    // root. Because the custom parts become real children of the existing root,
    // they inherit the exact renderer transforms/texture without touching either
    // locomotive renderer or the accepted shared wheel renderer.
    private static final Map<Object, Y18NCustomRig> Y18N_CUSTOM =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> Y18N_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Y18NCustomRig(
            ModelPart nearRod, ModelPart farRod,
            java.util.List<ModelPart> nearPins,
            java.util.List<ModelPart> farPins,
            float nearX, float farX, float restY, float restZ,
            float[] driverZ) {}
    private record Y18LBounds(float minX, float minY, float minZ,
                              float maxX, float maxY, float maxZ) {
        float dx() { return maxX - minX; }
        float dy() { return maxY - minY; }
        float dz() { return maxZ - minZ; }
        float centerX() { return (minX + maxX) * 0.5F; }
        float centerY() { return (minY + maxY) * 0.5F; }
        float centerZ() { return (minZ + maxZ) * 0.5F; }
    }

    // DEV38 y17 is a non-destructive live tuning layer over the exact y16
    // animation. If the external config is absent/has no active keys, the
    // original y16 displacement is retained byte-for-byte through the switch.
    // DEV42 v1a supplies orientation/pairs, but NOT authoritative crankpins.
    private static final java.nio.file.Path Y17_TUNE_PATH =
            java.nio.file.Path.of("config", "tc_dev38_y17_rod_tuning.properties");
    private static volatile java.util.Properties Y17_TUNING =
            new java.util.Properties();
    private static volatile int Y17_TUNING_REV = 0;
    private static volatile int Y17_TUNING_HASH = Integer.MIN_VALUE;
    private static long Y17_NEXT_REFRESH_NANOS = 0L;
    private static final java.util.Set<String> Y17_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // y18 is an independent, reversible, read-only-candidate physics overlay.
    // The y17 profile is never edited by this layer. One Heavy Steam mirrored
    // pair is active in the initial profile; all other rods keep y17 behavior.
    // Never use candidate scores as exact crankpin or axle coordinates.
    private static final java.nio.file.Path Y18_GEOMETRY_PATH =
            java.nio.file.Path.of("config", "tc_dev38_y18_rod_geometry.properties");
    private static volatile java.util.Properties Y18_CFG = new java.util.Properties();
    private static volatile int Y18_REV = 0;
    private static volatile int Y18_HASH = Integer.MIN_VALUE;
    private static long Y18_NEXT_RELOAD = 0L;
    private static final java.util.Set<String> Y18_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private FleetRodPistonPartAnimator() {}

    public static void applyDelegate(
            Object delegate,
            net.minecraft.world.entity.Entity entity,
            float partialTick) {

        if (delegate == null || entity == null) return;

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                        .getKey(entity.getType());

        if (key == null
                || !"traincraft".equals(key.getNamespace())
                || !TARGETS.contains(key.getPath())) {
            return;
        }

        String id = key.getPath();
        Object model = rendererModel(delegate);
        float phase = (float)
                RollingStockWheelAnimation.sampleNativePhase(entity, partialTick);

        if (model == null) {
            if (TREE_LOGGED.add(id)) {
                LOG.info(
                        "[TC-ROD-y16-TREE] id={} delegate={} model=NULL phase={}",
                        id,
                        delegate.getClass().getName(),
                        phase);
            }
            return;
        }

        Map<String, ModelPart> parts = modelParts(model);
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String name : namesFor(id)) {
            if (parts.containsKey(name)) {
                found.add(name);
            } else {
                missing.add(name);
            }
        }

        if (TREE_LOGGED.add(id)) {
            LOG.info(
                    "[TC-ROD-y16-TREE] id={} delegate={} model={} "
                            + "treeParts={} found={} missing={} phase={} "
                            + "outliersPending=[heavy:part_022,part_035; "
                            + "v60:box5,box6,box76,box77]",
                    id,
                    delegate.getClass().getName(),
                    model.getClass().getName(),
                    parts.size(),
                    found,
                    missing,
                    phase);
        }

        if (found.isEmpty()) return;

        if (Math.abs(phase) >= 0.05F && MOTION_LOGGED.add(id)) {
            LOG.info(
                    "[TC-ROD-y16-MOTION] id={} phase={} found={}",
                    id,
                    phase,
                    found);
        }

        apply(id, model, phase);
    }

    public static void apply(String id, Object model, float phase) {
        if (id == null || model == null || !TARGETS.contains(id)) return;

        final Rig rig;
        synchronized (CACHE) {
            Rig existing = CACHE.get(model);
            if (existing == null || !id.equals(existing.id)) {
                existing = build(id, model);
                CACHE.put(model, existing);
            }
            rig = existing;
        }

        if (rig == null || rig.parts.isEmpty()) return;
        // Refresh no more frequently than once per second; no build/restart
        // required to change any allowlisted selected rod in the six-train rig.
        reloadY17TuningIfDue();
        rig.restore();

        switch (id) {
            case "locomotive_steam_heavy" -> applyHeavy(rig, phase);
            case "locomotive_steam_mogul" -> applyMogul(rig, phase);
            case "locomotive_steam_forney" -> applyForney(rig, phase);
            case "locomotive_steam_br01" -> applyBr01(rig, phase);
            case "locomotive_steam_ussr" -> applyUssr(rig, phase);
            case "locomotive_diesel_v60" -> applyV60(rig, phase);
            default -> { }
        }
        // Overrides run AFTER y16 orbit; the exact y16 result remains the
        // fallback when the profile contains no active entries for this rig.
        applyY17Tuning(rig, phase);
        // The y18 mesh-length-verified pose is applied LAST, to override only
        // the explicitly enabled Heavy upper pair (or other future opt-ins).
        // All other rods exactly preserve the preceding y17 result.
        reloadY18GeometryIfDue();
        applyY18Geometry(rig, phase);
        // DEV38 y18d: Heavy Steam mirrored upper rods ONLY; opt-in overlay.
        // It is applied after y18a and before the untouched y18b telemetry.
        // This is a rest-pose-calibrated CANDIDATE pin, not a proven axle pin.
        if ("locomotive_steam_heavy".equals(id)) {
            y18dReloadIfDue();
            y18dApplyHeavyRestPin(rig, model, phase);
        }

        // DEV38 y18n: keep y18m center-pivot Forney wheels, retire the legacy
        // Mogul/Forney linkage families, and attach our own root-child coupling
        // rods + crank pins. Runs last so legacy rod writes cannot reappear.
        y18mApplyMogulForney(id, model, rig, phase);

        // Bounded diagnostics for next-chat source-grounded validation.
        // This logs actual phase and a real moved ModelPart delta once every
        // 90 render callbacks, exposing a static-phase / reset problem without
        // any further model, wheel or movement instrumentation.
        int frames = FRAME_COUNTS.computeIfAbsent(id,
                ignored -> new java.util.concurrent.atomic.AtomicInteger())
                .incrementAndGet();
        if (frames == 1 || frames % 90 == 0) {
            Base sample = rig.parts.values().iterator().next();
            LOG.info(
                    "[TC-ROD-y16-STEP] id={} frame={} phase={} "
                            + "movingPartCount={} sampleDxPx={} sampleDyPx={} sampleDzPx={}",
                    id, frames, phase, rig.parts.size(),
                    sample.part.x - sample.x,
                    sample.part.y - sample.y,
                    sample.part.z - sample.z);
        }
        // DEV38 y18b: no mutation, only bounded Heavy Steam geometry samples.
        // Executed after y17/y18a so observations reflect the rendered pose.
        if ("locomotive_steam_heavy".equals(id)
                && (frames == 1 || frames % 90 == 0)) {
            y18bProbeHeavy(model, rig, phase, frames);
        }
        // DEV38 y18e: observational ONLY. No writes to ModelPart, phase,
        // entity, or frozen wheel overlays. Debug samples are 90 callbacks
        // apart and limited to Heavy Steam. ModelPart axle evidence cannot
        // by itself prove the separately baked wheel-face orientation.
        if ("locomotive_steam_heavy".equals(id)
                && (frames == 1 || frames % 90 == 0)) {
            HeavyWheelAxisReadOnlyProbe.observe(modelParts(model), phase, frames);
        }
    }

    private static void applyHeavy(Rig rig, float phase) {
        orbitXY(rig, phase, 2.20F,
                "part_020", "part_029", "part_036", "part_037");
    }

    // DEV38 y18k: Mogul/Forney are handled from runtime cube geometry after
    // all legacy name-based tuning.  The y18j name mapping visibly selected
    // the wrong pieces, so these two switch arms intentionally do no mutation.
    private static void applyMogul(Rig rig, float phase) {
        // y18k geometry pass below.
    }

    private static void applyForney(Rig rig, float phase) {
        // y18k geometry pass below.
    }

    private static void applyBr01(Rig rig, float phase) {
        // DEV44 consolidated linkage: preserve the accepted main side-rod orbit.
        orbitXY(rig, phase, 2.20F, "part_007", "part_028");
        // Authored mirrored 21px connecting rods and 20px return links.
        // Move their crank-facing pivot with the existing driver phase.
        // Lengths and paired part names come from Br01SteamLocomotiveModel.
        applyCrankFacingLink(rig, phase, 2.20F, 21.0F,
                "part_006", "part_058");
        applyCrankFacingLink(rig, phase, 2.20F, 20.0F,
                "part_026", "part_030");
    }

    private static void applyUssr(Rig rig, float phase) {
        // DEV44 consolidated linkage: preserve the accepted main side-rod orbit.
        orbitXY(rig, phase, 1.80F, "part_046", "part_071");
        // Authored mirrored 16px connecting rods and 20px return links.
        applyCrankFacingLink(rig, phase, 1.80F, 16.0F,
                "part_042", "part_055");
        applyCrankFacingLink(rig, phase, 1.80F, 20.0F,
                "part_049", "part_072");
    }

    /**
     * DEV44 source-grounded crank-facing linkage. This moves only explicitly
     * named short auxiliary rods. Translation is phase-zero-neutral and shares
     * exactly the same wheel phase/radius as the accepted long side rods.
     * The bounded tilt is a first-order endpoint-following approximation; do
     * not interpret it as an axle/piston-pin calibration or final visual PASS.
     */
    private static void applyCrankFacingLink(
            Rig rig, float phase, float radiusPx, float lengthPx,
            String... names) {
        float dx = ((float) Math.cos(phase) - 1.0F) * radiusPx;
        float dy = (float) Math.sin(phase) * radiusPx;
        float tilt = (float) Math.asin(Math.max(-0.95F,
                Math.min(0.95F, dy / lengthPx)));
        for (String name : names) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.x = base.x + dx;
            base.part.y = base.y + dy;
            base.part.zRot = base.zRot + tilt;
        }
    }

    private static void applyV60(Rig rig, float phase) {
        // Original Traincraft ModelV60 box4/box83: mirrored 33x2x1 side rods.
        // box5/6/76/77 remain masked; they are 56px zero-height source sheets.
        // All six crank pins rest at Y=4px, with wheel axle centers at Y=6px.
        // Restrict throw to 2px so upper/lower extremes stay within the wheels.
        // The long coupling rods move identically to stay attached to all pins.
        // At phase zero the original rod geometry is preserved exactly.
        final float crankThrowPx = 2.0F;
        final float dx = (float) Math.sin(phase) * crankThrowPx;
        final float dy = (1.0F - (float) Math.cos(phase)) * crankThrowPx;
        for (String name : new String[] {
                "box4", "box83",
                "tc_v60_pin_neg_0", "tc_v60_pin_neg_1", "tc_v60_pin_neg_2",
                "tc_v60_pin_pos_0", "tc_v60_pin_pos_1", "tc_v60_pin_pos_2"}) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.x = base.x + dx;
            base.part.y = base.y + dy;
        }
    }

    /**
     * DEV42 v1a sourced the exact model/part names and their primary long
     * axes. Mogul and Forney have axle-X wheels (rods longitudinal Z); the
     * other four selected models have axle-Z wheels (rods longitudinal X).
     * These axes are gated by known model IDs rather than candidate scores.
     * No crankpin or real piston stroke is inferred from bounding boxes.
     */
    private static void applyY17Tuning(Rig rig, float phase) {
        final java.util.Properties cfg = Y17_TUNING;
        final String id = rig.id;
        final boolean zy = "locomotive_steam_mogul".equals(id)
                || "locomotive_steam_forney".equals(id);
        final float originalRadius = y17OriginalRadiusPx(id);
        final String enginePrefix = id + ".";

        for (Map.Entry<String, Base> entry : rig.parts.entrySet()) {
            String name = entry.getKey();
            String partPrefix = enginePrefix + name + ".";
            if (!y17HasOverride(cfg, enginePrefix)
                    && !y17HasOverride(cfg, partPrefix)) {
                continue;
            }

            Base base = entry.getValue();
            String mode = y17Text(cfg, partPrefix + "mode",
                    y17Text(cfg, enginePrefix + "mode", "ORBIT"))
                    .trim().toUpperCase(java.util.Locale.ROOT);
            float radius = y17Number(cfg, partPrefix + "radiusPx",
                    y17Number(cfg, enginePrefix + "radiusPx",
                            originalRadius, 0F, 5F), 0F, 5F);
            float shiftDegrees = y17Number(cfg, partPrefix + "phaseDeg",
                    y17Number(cfg, enginePrefix + "phaseDeg",
                            0F, -180F, 180F), -180F, 180F);
            float px = y17Number(cfg, partPrefix + "offsetXPx",
                    0F, -8F, 8F);
            float py = y17Number(cfg, partPrefix + "offsetYPx",
                    0F, -8F, 8F);
            float pz = y17Number(cfg, partPrefix + "offsetZPx",
                    0F, -8F, 8F);
            float phaseShift = (float) Math.toRadians(shiftDegrees);
            float horizontal = ((float) Math.cos(phase + phaseShift)
                    - (float) Math.cos(phaseShift)) * radius;
            float vertical = ((float) Math.sin(phase + phaseShift)
                    - (float) Math.sin(phaseShift)) * radius;

            switch (mode) {
                case "STATIC" -> {
                    base.part.x = base.x + px;
                    base.part.y = base.y + py;
                    base.part.z = base.z + pz;
                }
                case "SLIDE" -> { // Diagnostic only: not a validated slider-crank.
                    base.part.x = base.x + px + (zy ? 0F : horizontal);
                    base.part.y = base.y + py;
                    base.part.z = base.z + pz + (zy ? horizontal : 0F);
                }
                case "ORBIT" -> {
                    base.part.x = base.x + px + (zy ? 0F : horizontal);
                    base.part.y = base.y + py + vertical;
                    base.part.z = base.z + pz + (zy ? horizontal : 0F);
                }
                default -> {
                    // Fail closed for this part. Unknown modes cannot cause
                    // arbitrary motion or affect wheels/other model children.
                    continue;
                }
            }

            String diagnosticKey = Y17_TUNING_REV + "|" + id + "|" + name;
            if (Y17_LOGGED.add(diagnosticKey)) {
                LOG.info("[TC-ROD-y17-TUNE] rev={} id={} part={} "
                                + "mode={} plane={} rPx={} phaseDeg={} "
                                + "offsetPx=({},{},{})",
                        Y17_TUNING_REV, id, name, mode, zy ? "ZY" : "XY",
                        radius, shiftDegrees, px, py, pz);
            }
        }
    }

    private static float y17OriginalRadiusPx(String id) {
        return switch (id) {
            case "locomotive_steam_heavy", "locomotive_steam_br01" -> 2.20F;
            case "locomotive_diesel_v60" -> 1.60F;
            case "locomotive_steam_mogul", "locomotive_steam_forney",
                    "locomotive_steam_ussr" -> 1.80F;
            default -> 0F;
        };
    }

    private static boolean y17HasOverride(java.util.Properties props, String prefix) {
        for (String key : new String[] {
                "mode", "radiusPx", "phaseDeg",
                "offsetXPx", "offsetYPx", "offsetZPx"}) {
            if (props.containsKey(prefix + key)) return true;
        }
        return false;
    }

    private static String y17Text(
            java.util.Properties props, String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    private static float y17Number(
            java.util.Properties props,
            String key,
            float fallback,
            float lo,
            float hi) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        try {
            float val = Float.parseFloat(raw.trim());
            if (!Float.isFinite(val)) return fallback;
            return Math.max(lo, Math.min(hi, val));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void reloadY17TuningIfDue() {
        long now = System.nanoTime();
        if (now < Y17_NEXT_REFRESH_NANOS) return;
        Y17_NEXT_REFRESH_NANOS = now + 1_000_000_000L;

        try {
            byte[] bytes = java.nio.file.Files.isRegularFile(Y17_TUNE_PATH)
                    ? java.nio.file.Files.readAllBytes(Y17_TUNE_PATH)
                    : new byte[0];
            if (bytes.length > 65536) {
                LOG.warn("[TC-ROD-y17-CONFIG] rejected >64KiB file {}",
                        Y17_TUNE_PATH);
                return;
            }
            int fingerprint = java.util.Arrays.hashCode(bytes);
            if (fingerprint == Y17_TUNING_HASH) return;

            java.util.Properties fresh = new java.util.Properties();
            try (java.io.StringReader r = new java.io.StringReader(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8))) {
                fresh.load(r);
            }
            Y17_TUNING = fresh;
            Y17_TUNING_HASH = fingerprint;
            Y17_TUNING_REV++;
            Y17_LOGGED.clear();
            LOG.info("[TC-ROD-y17-CONFIG] revision={} profile={} "
                            + "keys={} hash={} defaultY16={}",
                    Y17_TUNING_REV, Y17_TUNE_PATH,
                    fresh.size(), Integer.toHexString(fingerprint),
                    fresh.isEmpty());
        } catch (java.io.IOException | IllegalArgumentException ex) {
            // Keep the last valid profile on a transient write/read error.
            LOG.warn("[TC-ROD-y17-CONFIG] profile reload ignored: {}",
                    ex.toString());
        }
    }

    // SOURCE: DEV42 java_parts.csv, 2026-10-06T21:19:16.
    // This map describes measured slender-box LENGTH and long-axis, not an
    // authoritative classification of the crank end. Each linkage remains a
    // reversible trial until in-game mechanical attachments are verified.
    private static Y18Evidence y18Evidence(String id, String part) {
        return switch (id + ":" + part) {
            case "locomotive_steam_heavy:part_020",
                 "locomotive_steam_heavy:part_037" -> new Y18Evidence(23.0F, false);
            case "locomotive_steam_mogul:part_022",
                 "locomotive_steam_mogul:part_023" -> new Y18Evidence(9.0F, true);
            case "locomotive_steam_br01:part_006",
                 "locomotive_steam_br01:part_058" -> new Y18Evidence(21.0F, false);
            case "locomotive_steam_ussr:part_042",
                 "locomotive_steam_ussr:part_055" -> new Y18Evidence(16.0F, false);
            default -> null;
        };
    }

    private record Y18Evidence(float lengthPx, boolean axisZ) {}
    private record Y18Bounds(float lengthPx, float cx, float cy, float cz) {}

    /**
     * Center a long thin ModelPart on a two-end slider-crank solution:
     *   crank = (r(cos(p)-1), r sin(p));
     *   piston = crank +/- sqrt(L^2 - crankY^2) -/+ L.
     * Segment rotation uses atan2 of the crank height over projected length.
     * A local cube centroid is measured from the ACTUAL rendered ModelPart,
     * then the pivot is compensated so the rod CENTER reaches the correct
     * geometric midpoint. This avoids assuming ModelPart pivot=center.
     * It preserves authored rest-pose at phase zero, and all changes are
     * allowlisted and reversible. It does not create hidden piston geometry.
     */
    private static void applyY18Geometry(Rig rig, float phase) {
        java.util.Properties cfg = Y18_CFG;
        if (!"true".equalsIgnoreCase(cfg.getProperty("y18.enabled", "false"))) return;
        for (Map.Entry<String, Base> e : rig.parts.entrySet()) {
            String name = e.getKey();
            Y18Evidence ev = y18Evidence(rig.id, name);
            if (ev == null) continue;
            String prefix = rig.id + "." + name + ".";
            String mode = cfg.getProperty(prefix + "mode", "OFF")
                    .trim().toUpperCase(java.util.Locale.ROOT);
            if (!"SLIDER_CRANK".equals(mode)) continue;
            Base base = e.getValue();
            String logKey = Y18_REV + ":" + rig.id + ":" + name;
            // Runtime y18 established BOTH heavy upper mirrored rods rest at
            // exactly -4 degrees in ModelPart Z. Accept ONLY that measured
            // orientation on those two nodes; keep every other rotation
            // rejected exactly as before. This is not a blanket bypass.
            boolean authoredHeavy = "locomotive_steam_heavy".equals(rig.id)
                    && ("part_020".equals(name) || "part_037".equals(name));
            float expectedHeavyZ = (float) Math.toRadians(-4.0);
            boolean unexpectedRotation = Math.abs(base.xRot) > 0.02F
                    || Math.abs(base.yRot) > 0.02F
                    || (authoredHeavy
                        ? Math.abs(base.zRot - expectedHeavyZ) > 0.008F
                        : Math.abs(base.zRot) > 0.02F);
            if (unexpectedRotation) {
                if (Y18_LOGGED.add(logKey)) {
                    LOG.warn("[TC-ROD-y18a-GUARD] id={} part={} reason=UNEXPECTED_AUTHORED_ROTATION x={} y={} z={} expectedHeavyZ={} fallback=y17",
                            rig.id, name, base.xRot, base.yRot, base.zRot,
                            authoredHeavy ? expectedHeavyZ : 0.0F);
                }
                continue;
            }
            Y18Bounds bounds = y18LocalBounds(base.part, ev.axisZ());
            if (bounds == null || Math.abs(bounds.lengthPx() - ev.lengthPx()) > 0.65F) {
                if (Y18_LOGGED.add(logKey)) {
                    LOG.warn("[TC-ROD-y18-GUARD] id={} part={} reason=MESH_LENGTH sourceLenPx={} actualLenPx={} fallback=y17",
                            rig.id, name, ev.lengthPx(),
                            bounds == null ? "NA" : Float.toString(bounds.lengthPx()));
                }
                continue;
            }
            float radius = y17Number(cfg, prefix + "radiusPx",
                    y17OriginalRadiusPx(rig.id), 0.25F, 3.0F);
            if (radius >= ev.lengthPx() * 0.4F) {
                if (Y18_LOGGED.add(logKey)) {
                    LOG.warn("[TC-ROD-y18-GUARD] id={} part={} reason=RADIUS_TOO_LARGE fallback=y17",
                            rig.id, name);
                }
                continue;
            }
            String end = cfg.getProperty(prefix + "crankEnd", "PLUS");
            int endSign = "PLUS".equalsIgnoreCase(end.trim()) ? 1
                    : "MINUS".equalsIgnoreCase(end.trim()) ? -1 : 0;
            if (endSign == 0) continue;
            float shift = (float) Math.toRadians(y17Number(cfg,
                    prefix + "phaseDeg", 0F, -90F, 90F));
            float angle = phase + shift;
            float initialAngle = shift;
            // Normalize motion relative to the authored rest pose. With a
            // phase offset, subtract the same mechanism state at phase=0.
            float crankRun = radius * ((float)Math.cos(angle)
                    - (float)Math.cos(initialAngle));
            float crankRise = radius * ((float)Math.sin(angle)
                    - (float)Math.sin(initialAngle));
            // Preserve the authored -4 degree tilt at rest. Treat one rod
            // endpoint as a crank and the other as a horizontal piston guide.
            // Both endpoints are measured relative to the ORIGINAL angled
            // rod, rather than wrongly assuming a horizontal rest vector.
            float authoredAngle = ev.axisZ() ? -base.xRot : base.zRot;
            float restSin = (float)Math.sin(authoredAngle);
            float restCos = (float)Math.cos(authoredAngle);
            float length = ev.lengthPx();
            float vertical = length * restSin + endSign * crankRise;
            float squared = length*length - vertical*vertical;
            if (!Float.isFinite(squared) || squared <= 0.0F) {
                if (Y18_LOGGED.add(logKey)) {
                    LOG.warn("[TC-ROD-y18a-GUARD] id={} part={} reason=SLIDER_DISCRIMINANT fallback=y17",
                            rig.id, name);
                }
                continue;
            }
            float projected = (float)Math.sqrt(squared);
            // The piston moves only along the original horizontal slider.
            // At phase zero these are both exactly zero (up to float error).
            float pistonRun = crankRun + endSign * (length*restCos - projected);
            float centerRun = (crankRun + pistonRun) * 0.5F;
            float centerRise = crankRise * 0.5F;
            float finalAngle = (float)Math.atan2(vertical, projected);
            float tilt = finalAngle - authoredAngle;
            if (!Float.isFinite(centerRun) || !Float.isFinite(centerRise)
                    || !Float.isFinite(tilt) || Math.abs(tilt) > 0.45F) {
                if (Y18_LOGGED.add(logKey)) {
                    LOG.warn("[TC-ROD-y18a-GUARD] id={} part={} reason=INVALID_POSE fallback=y17",
                            rig.id, name);
                }
                continue;
            }
            // Correct ModelPart pivot compensation uses:
            //  R(authored+tilt)*localCentroid - R(authored)*localCentroid.
            // Compensating against R(0) caused wrong positioning for -4 deg.
            float c0 = (float)Math.cos(authoredAngle);
            float s0 = (float)Math.sin(authoredAngle);
            float c1 = (float)Math.cos(finalAngle);
            float s1 = (float)Math.sin(finalAngle);
            if (!ev.axisZ()) {
                float localX = bounds.cx();
                float localY = bounds.cy();
                float deltaX = localX*(c1-c0) - localY*(s1-s0);
                float deltaY = localX*(s1-s0) + localY*(c1-c0);
                base.part.x = base.x + centerRun - deltaX;
                base.part.y = base.y + centerRise - deltaY;
                base.part.z = base.z;
                base.part.zRot = base.zRot + tilt;
            } else {
                // The axis-Z branch remains unchanged for zero-rest-angle
                // candidates (which are OFF in the initial y18 profile).
                float localZ = bounds.cz();
                float localY = bounds.cy();
                float deltaZ = -localY*(s1-s0) + localZ*(c1-c0);
                float deltaY = localY*(c1-c0) + localZ*(s1-s0);
                base.part.z = base.z + centerRun - deltaZ;
                base.part.y = base.y + centerRise - deltaY;
                base.part.x = base.x;
                base.part.xRot = base.xRot - tilt;
            }
            if (Y18_LOGGED.add(logKey)) {
                LOG.info("[TC-ROD-y18a-GEOM] rev={} id={} part={} plane={} sourceLenPx={} measuredLenPx={} authoredRestDeg={} radiusPx={} crankEnd={} phaseDeg={} centerLocalPx=({},{},{}) fallback=y17-on-disable",
                        Y18_REV, rig.id, name, ev.axisZ()?"ZY":"XY",
                        ev.lengthPx(), bounds.lengthPx(),
                        (float)Math.toDegrees(authoredAngle), radius, end,
                        (float)Math.toDegrees(shift),
                        bounds.cx(), bounds.cy(), bounds.cz());
            }
        }
    }

    // DEV38 y18d rest-pin trial, isolated from the frozen y17/y18 settings.
    // The wheel CENTER is measured from the model; the crankpin radius is
    // the distance between that center and the AUTHORED rod endpoint at rest.
    // The historical 2.2px y16 orbit is NOT treated as a measured crank radius.
    // This produces a reversible test, not an authoritative crankpin model.
    private static final java.nio.file.Path Y18D_TRIAL_PATH =
            java.nio.file.Path.of("config", "tc_dev38_y18d_heavy_restpin.properties");
    private static volatile java.util.Properties Y18D_TRIAL_CFG = new java.util.Properties();
    private static volatile long Y18D_NEXT_READ_NS = 0L;
    private static volatile int Y18D_CONFIG_HASH = Integer.MIN_VALUE;
    private static final java.util.Set<String> Y18D_GUARD_ONCE =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicInteger Y18D_SAMPLES =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void y18dReloadIfDue() {
        long now = System.nanoTime();
        if (now < Y18D_NEXT_READ_NS) return;
        Y18D_NEXT_READ_NS = now + 1_000_000_000L;
        try {
            byte[] b = java.nio.file.Files.isRegularFile(Y18D_TRIAL_PATH)
                    ? java.nio.file.Files.readAllBytes(Y18D_TRIAL_PATH) : new byte[0];
            if (b.length > 2048) { y18dGuard("CONFIG_TOO_LARGE"); return; }
            int h = java.util.Arrays.hashCode(b);
            if (h == Y18D_CONFIG_HASH) return;
            java.util.Properties cfg = new java.util.Properties();
            try (java.io.StringReader r = new java.io.StringReader(
                    new String(b, java.nio.charset.StandardCharsets.UTF_8))) {
                cfg.load(r);
            }
            // Only true/false and +1/-1 are allowed, no tuning offsets.
            String enabled = cfg.getProperty("enabled", "false").trim();
            String sign = cfg.getProperty("phaseSign", "1").trim();
            if (!("true".equalsIgnoreCase(enabled) || "false".equalsIgnoreCase(enabled))
                    || !("1".equals(sign) || "-1".equals(sign))) {
                y18dGuard("BAD_CONFIG_VALUES"); return;
            }
            for (Object key : cfg.keySet()) {
                if (!"enabled".equals(key) && !"phaseSign".equals(key)) {
                    y18dGuard("UNKNOWN_CONFIG_KEY"); return;
                }
            }
            Y18D_TRIAL_CFG = cfg;
            Y18D_CONFIG_HASH = h;
            Y18D_GUARD_ONCE.clear();
            LOG.info("[TC-ROD-y18d-CONFIG] enabled={} phaseSign={} mode=REST_ANCHORED_WHEEL_CANDIDATE pistonGuide=AUTHORED_Y",
                    enabled, sign);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            y18dGuard("CONFIG_READ_ERROR:" + ex.getClass().getSimpleName());
        }
    }

    private static void y18dGuard(String reason) {
        if (Y18D_GUARD_ONCE.add(reason)) {
            LOG.warn("[TC-ROD-y18d-GUARD] reason={} fallback=y18a noWheelEdits=true", reason);
        }
    }

    private static void y18dApplyHeavyRestPin(Rig rig, Object model, float phase) {
        java.util.Properties cfg = Y18D_TRIAL_CFG;
        if (!"true".equalsIgnoreCase(cfg.getProperty("enabled", "false"))) return;
        int sign = "-1".equals(cfg.getProperty("phaseSign", "1")) ? -1 : 1;
        Map<String, ModelPart> all = modelParts(model);
        String[][] pairs = {{"part_020", "part_014"}, {"part_037", "part_034"}};
        int tick = Y18D_SAMPLES.incrementAndGet();
        for (String[] pair : pairs) {
            String rodName = pair[0], hubName = pair[1];
            Base base = rig.parts.get(rodName);
            ModelPart hub = all.get(hubName);
            if (base == null || hub == null) {
                y18dGuard("MISSING_PART:" + rodName); continue;
            }
            ModelPart rod = base.part;
            String rodPath = y18bPartPath(model, all, rodName, rod);
            String hubPath = y18bPartPath(model, all, hubName, hub);
            if (!y18bParentPath(rodPath).equals(y18bParentPath(hubPath))
                    || "UNKNOWN".equals(y18bParentPath(rodPath))) {
                y18dGuard("DIFFERENT_PARENT:" + rodName); continue;
            }
            Y18Bounds rb = y18LocalBounds(rod, false);
            Y18Bounds wb = y18LocalBounds(hub, false);
            if (rb == null || wb == null || Math.abs(rb.lengthPx() - 23F) > 0.30F
                    || Math.abs(wb.lengthPx() - 11F) > 0.30F
                    || Math.abs(y18bCubeHeight(hub) - 11F) > 0.30F
                    || Math.abs(y18bCubeDepth(hub) - 1F) > 0.30F) {
                y18dGuard("MESH_DIMENSIONS:" + rodName); continue;
            }
            if (Math.abs(base.xRot) > 0.001F || Math.abs(base.yRot) > 0.001F
                    || Math.abs(base.zRot - (float)Math.toRadians(-4.0)) > 0.008F
                    || Math.abs(hub.xRot) > 0.001F || Math.abs(hub.yRot) > 0.001F) {
                y18dGuard("UNEXPECTED_ROTATION:" + rodName); continue;
            }
            // Compute BOTH original endpoints from the model's actual local
            // centroid + authored -4deg + its original pivot (not y18a output).
            float ca = (float)Math.cos(base.zRot), sa = (float)Math.sin(base.zRot);
            float half = rb.lengthPx() * 0.5F;
            float minusLocalX = rb.cx() - half, plusLocalX = rb.cx() + half;
            float restMinusX = base.x + ca*minusLocalX - sa*rb.cy();
            float restMinusY = base.y + sa*minusLocalX + ca*rb.cy();
            float restPlusX  = base.x + ca*plusLocalX  - sa*rb.cy();
            float restPlusY  = base.y + sa*plusLocalX  + ca*rb.cy();
            // Determine candidate wheel hub center in the SAME parent's
            // model-pixel frame. No guessed world-space / camera offset.
            float cw = (float)Math.cos(hub.zRot), sw = (float)Math.sin(hub.zRot);
            float hubX = hub.x + cw*wb.cx() - sw*wb.cy();
            float hubY = hub.y + sw*wb.cx() + cw*wb.cy();
            float hubZ = hub.z + wb.cz();
            float rodZ = base.z + rb.cz();
            float expectedZ = "part_020".equals(rodName) ? 6.5F : -6.5F;
            if (Math.abs(hubX + 16.5F) > 0.25F
                    || Math.abs(hubY - 4.5F) > 0.25F
                    || Math.abs(hubZ - expectedZ) > 0.25F
                    || Math.abs(rodZ - ("part_020".equals(rodName) ? 8.5F : -8.5F)) > 0.25F) {
                y18dGuard("HUB_COORDINATES:" + rodName); continue;
            }
            if (Math.abs(restPlusX - (-18.021149F)) > 0.25F
                    || Math.abs(restPlusY - 4.8943834F) > 0.25F
                    || Math.abs(restMinusX - (-40.965122F)) > 0.25F
                    || Math.abs(restMinusY - 6.498782F) > 0.25F) {
                y18dGuard("AUTHORED_ENDPOINTS:" + rodName); continue;
            }
            float dx0 = restPlusX - hubX, dy0 = restPlusY - hubY;
            float radius = (float)Math.hypot(dx0, dy0);
            if (!Float.isFinite(radius) || radius < 1.30F || radius > 1.85F) {
                y18dGuard("REST_RADIUS_OUT_OF_RANGE:" + rodName); continue;
            }
            float angle = sign * phase;
            float cp = (float)Math.cos(angle), sp = (float)Math.sin(angle);
            float plusX = hubX + dx0 * cp - dy0 * sp;
            float plusY = hubY + dx0 * sp + dy0 * cp;
            // Rigid rod: crank end follows the rest-anchored wheel circle.
            // Other end slides HORIZONTALLY along original piston guide Y.
            float vertical = plusY - restMinusY;
            float projectedSq = rb.lengthPx()*rb.lengthPx() - vertical*vertical;
            if (!Float.isFinite(projectedSq) || projectedSq <= 0F) {
                y18dGuard("NO_REAL_SLIDER_INTERSECTION:" + rodName); continue;
            }
            float projected = (float)Math.sqrt(projectedSq);
            float minusX = plusX - projected;
            float minusY = restMinusY;
            float newAngle = (float)Math.atan2(vertical, projected);
            float targetCenterX = (plusX + minusX) * 0.5F;
            float targetCenterY = (plusY + minusY) * 0.5F;
            float c = (float)Math.cos(newAngle), s = (float)Math.sin(newAngle);
            float newPivotX = targetCenterX - (c*rb.cx() - s*rb.cy());
            float newPivotY = targetCenterY - (s*rb.cx() + c*rb.cy());
            if (!Float.isFinite(newPivotX) || !Float.isFinite(newPivotY)
                    || !Float.isFinite(newAngle) || Math.abs(newAngle - base.zRot) > 0.25F
                    || Math.abs(newPivotX - base.x) > 5.0F) {
                y18dGuard("IMPLAUSIBLE_POSE:" + rodName); continue;
            }
            rod.x = newPivotX;
            rod.y = newPivotY;
            rod.z = base.z;
            rod.xRot = base.xRot;
            rod.yRot = base.yRot;
            rod.zRot = newAngle;
            if (tick == 1 || tick % 90 == 0) {
                float radialError = Math.abs((float)Math.hypot(plusX-hubX, plusY-hubY)-radius);
                LOG.info("[TC-ROD-y18d-POSE] id={} part={} hub={} phase={} sign={} "
                        + "restRadiusPx={} centerPx=({},{}) crankTipPx=({},{}) "
                        + "sliderTipPx=({},{}) guideYPx={} wheelCircleErrorPx={} "
                        + "poseAngleDeg={} validation=REST_PIN_CANDIDATE_ONLY",
                        rig.id, rodName, hubName, phase, sign, radius, hubX, hubY,
                        plusX, plusY, minusX, minusY, restMinusY, radialError,
                        Math.toDegrees(newAngle));
            }
        }
    }

    // Y18B: read-only joint/axle candidate diagnostics. This is not an
    // authoritative wheel crankpin measurement: accepted wheel-face overlays
    // are rendered separately, and no model-cube score establishes pins.
    // All coordinates below are in the SAME immediate parent's pixel frame;
    // parent mismatches are labelled and excluded from ranking.
    private static final String[] Y18B_HEAVY_WHEEL_CANDIDATES = {
            "part_000", "part_001", "part_002", "part_003", "part_014",
            "part_018", "part_034", "part_046", "part_050", "part_051"
    };
    private static final String[] Y18B_HEAVY_RODS = {"part_020", "part_037"};

    private static void y18bProbeHeavy(Object model, Rig rig, float phase, int frame) {
        try {
            Map<String, ModelPart> all = modelParts(model);
            if (frame == 1) {
                LOG.info("[TC-ROD-y18b-INFO] id={} frame={} modelParts={} "
                        + "readOnly=true rodTips=box-end-centers wheelRefs=DEV42-wheel-candidates "
                        + "pinCenters=UNVERIFIED overlays=NOT_MEASURED sharedParentRequired=true",
                        rig.id, frame, all.size());
            }
            for (String rodName : Y18B_HEAVY_RODS) {
                Base base = rig.parts.get(rodName);
                if (base == null) {
                    if (frame == 1) LOG.warn("[TC-ROD-y18b-ABSENT] id={} part={}", rig.id, rodName);
                    continue;
                }
                ModelPart rod = base.part;
                if (Math.abs(rod.xRot) > 0.001F || Math.abs(rod.yRot) > 0.001F) {
                    LOG.warn("[TC-ROD-y18b-SKIP] part={} reason=ROD_XY_ROT_UNSUPPORTED x={} y={}",
                            rodName, rod.xRot, rod.yRot);
                    continue;
                }
                Y18Bounds b = y18LocalBounds(rod, false);
                if (b == null || Math.abs(b.lengthPx()-23F) > .65F) {
                    LOG.warn("[TC-ROD-y18b-SKIP] part={} reason=ROD_BOUNDS", rodName);
                    continue;
                }
                float s = (float)Math.sin(rod.zRot), c = (float)Math.cos(rod.zRot);
                float lowerX = b.cx() - b.lengthPx()*.5F;
                float upperX = b.cx() + b.lengthPx()*.5F;
                float negX = rod.x + c*lowerX - s*b.cy();
                float negY = rod.y + s*lowerX + c*b.cy();
                float posX = rod.x + c*upperX - s*b.cy();
                float posY = rod.y + s*upperX + c*b.cy();
                float sideZ = rod.z + b.cz();
                String rodPath = y18bPartPath(model, all, rodName, rod);
                LOG.info("[TC-ROD-y18b-TIPS] frame={} phase={} part={} path={} "
                        + "endMIN_XYpx=({}, {}) endPLUS_XYpx=({}, {}) zPx={} "
                        + "partPivotPx=({},{},{}) zRotDeg={} lengthPx={} "
                        + "y18.enabled={}",
                        frame, phase, rodName, rodPath, negX, negY, posX, posY,
                        sideZ, rod.x, rod.y, rod.z,
                        Math.toDegrees(rod.zRot), b.lengthPx(),
                        Y18_CFG.getProperty("y18.enabled", "false"));
                y18bBestCandidates(model, all, frame, phase, rodName, rodPath,
                        "MIN", negX, negY, sideZ);
                y18bBestCandidates(model, all, frame, phase, rodName, rodPath,
                        "PLUS", posX, posY, sideZ);
            }
        } catch (RuntimeException ex) {
            // This cannot alter model geometry even when reflection fails.
            if (frame == 1 || frame % 360 == 0)
                LOG.warn("[TC-ROD-y18b-ERROR] frame={} readOnly=true error={}",
                        frame, ex.toString());
        }
    }

    private static String y18bPartPath(
            Object model, Map<String, ModelPart> all, String name, ModelPart match) {
        // A direct ModelPart root has no rendered "root/" prefix in the
        // existing modelParts() map; recover that immediate-parent identity.
        if (model instanceof ModelPart root && children(root).get(name) == match)
            return "MODEL_ROOT/" + name;
        String best=name;
        for (Map.Entry<String, ModelPart> e : all.entrySet()) {
            String p=e.getKey();
            if (e.getValue() == match && p.length() > best.length()
                    && (p.equals(name) || p.endsWith("/"+name))) best=p;
        }
        return best;
    }

    private static String y18bParentPath(String path) {
        int cut=path.lastIndexOf('/');
        return cut < 0 ? "UNKNOWN" : path.substring(0,cut);
    }

    private static void y18bBestCandidates(
            Object model, Map<String, ModelPart> all, int frame, float phase,
            String rodName, String rodPath, String end,
            float tipX, float tipY, float tipZ) {
        String parent=y18bParentPath(rodPath);
        String bestName="NONE", bestPath="UNKNOWN";
        float bestResidual=Float.POSITIVE_INFINITY;
        float bestHubX=Float.NaN, bestHubY=Float.NaN, bestHubZ=Float.NaN;
        float bestRadialDistance=Float.NaN;
        int eligible=0, pathMismatches=0;
        // Current y18a Heavy radius. This is an observational radial-gap
        // hypothesis, NOT a measured wheel crankpin radius or attachment.
        float assumedRadius=2.2F;
        for (String candidate : Y18B_HEAVY_WHEEL_CANDIDATES) {
            ModelPart hub=all.get(candidate);
            if (hub == null) continue;
            String wheelPath=y18bPartPath(model,all,candidate,hub);
            String wheelParent=y18bParentPath(wheelPath);
            if ("UNKNOWN".equals(parent) || !parent.equals(wheelParent)) {
                ++pathMismatches; continue;
            }
            if (Math.abs(hub.xRot) > .001F || Math.abs(hub.yRot) > .001F) continue;
            Y18Bounds b=y18LocalBounds(hub,false);
            if (b == null) continue;
            float c=(float)Math.cos(hub.zRot), s=(float)Math.sin(hub.zRot);
            float cx=hub.x + c*b.cx() - s*b.cy();
            float cy=hub.y + s*b.cx() + c*b.cy();
            float cz=hub.z + b.cz();
            if (Math.abs(cz-tipZ) > 6F) continue; // only same-side candidates
            ++eligible;
            float radial=(float)Math.hypot(tipX-cx,tipY-cy);
            float error=Math.abs(radial-assumedRadius);
            if (frame == 1) {
                LOG.info("[TC-ROD-y18b-HUB] frame={} part={} path={} "
                        + "centerPx=({},{},{}) dimsPx=({}, {}, {}) zRotDeg={} "
                        + "wheelCenterCandidate=true visible={} NOT_CRANKPIN",
                        frame, candidate, wheelPath,cx,cy,cz,
                        b.lengthPx(),
                        y18bCubeHeight(hub),y18bCubeDepth(hub),
                        Math.toDegrees(hub.zRot), hub.visible);
            }
            if (error < bestResidual) {
                bestResidual=error;
                bestName=candidate;bestPath=wheelPath;
                bestHubX=cx;bestHubY=cy;bestHubZ=cz;
                bestRadialDistance=radial;
            }
        }
        LOG.info("[TC-ROD-y18b-PIN-CANDIDATE] frame={} phase={} rod={} end={} "
                + "wheel={} wheelPath={} wheelCenterPx=({},{},{}) "
                + "rodTipXYpx=({},{}) centerToTipPx={} "
                + "assumedCrankRadiusPx={} radialResidualPx={} "
                + "sameParentEligible={} pathMismatches={} "
                + "ranking=HYPOTHESIS_ONLY_NOT_PROVEN_PIN",
                frame,phase,rodName,end,bestName,bestPath,
                bestHubX,bestHubY,bestHubZ,tipX,tipY,bestRadialDistance,
                assumedRadius,bestResidual,eligible,pathMismatches);
    }

    private static float y18bCubeHeight(ModelPart part) {
        Object list=fieldValue(part,"cubes");
        if (!(list instanceof java.util.List<?> cubes)) return Float.NaN;
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        for (Object cube:cubes) {
            Float a=y18Field(cube,"minY"),b=y18Field(cube,"maxY");
            if(a==null || b==null) return Float.NaN;
            lo=Math.min(lo,a);hi=Math.max(hi,b);
        }
        return hi-lo;
    }

    private static float y18bCubeDepth(ModelPart part) {
        Object list=fieldValue(part,"cubes");
        if (!(list instanceof java.util.List<?> cubes)) return Float.NaN;
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        for (Object cube:cubes) {
            Float a=y18Field(cube,"minZ"),b=y18Field(cube,"maxZ");
            if(a==null || b==null) return Float.NaN;
            lo=Math.min(lo,a);hi=Math.max(hi,b);
        }
        return hi-lo;
    }

    /** For geometry guard, reflect real ModelPart cube min/max dimensions. */
    private static Y18Bounds y18LocalBounds(ModelPart part, boolean axisZ) {
        Object value = fieldValue(part, "cubes");
        if (!(value instanceof java.util.List<?> cubes) || cubes.isEmpty()) return null;
        float minX=Float.POSITIVE_INFINITY, minY=Float.POSITIVE_INFINITY,
                minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY, maxY=Float.NEGATIVE_INFINITY,
                maxZ=Float.NEGATIVE_INFINITY;
        for (Object cube : cubes) {
            if (cube == null) return null;
            Float a=y18Field(cube, "minX"), b=y18Field(cube, "maxX"),
                    c=y18Field(cube, "minY"), d=y18Field(cube, "maxY"),
                    e=y18Field(cube, "minZ"), f=y18Field(cube, "maxZ");
            if (a==null || b==null || c==null || d==null || e==null || f==null)
                return null;
            minX=Math.min(minX,a); maxX=Math.max(maxX,b);
            minY=Math.min(minY,c); maxY=Math.max(maxY,d);
            minZ=Math.min(minZ,e); maxZ=Math.max(maxZ,f);
        }
        float longLen = axisZ ? maxZ-minZ : maxX-minX;
        if (!Float.isFinite(longLen) || longLen < 0.1F) return null;
        return new Y18Bounds(longLen, (minX+maxX)/2F,
                (minY+maxY)/2F, (minZ+maxZ)/2F);
    }

    private static Float y18Field(Object object, String name) {
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            try {
                Field f=type.getDeclaredField(name);
                f.setAccessible(true);
                Object val=f.get(object);
                if (val instanceof Number n) return n.floatValue();
                return null;
            } catch (NoSuchFieldException ex) {
                type=type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    private static void reloadY18GeometryIfDue() {
        long now = System.nanoTime();
        if (now < Y18_NEXT_RELOAD) return;
        Y18_NEXT_RELOAD = now + 1_000_000_000L;
        try {
            byte[] bytes = java.nio.file.Files.isRegularFile(Y18_GEOMETRY_PATH)
                    ? java.nio.file.Files.readAllBytes(Y18_GEOMETRY_PATH) : new byte[0];
            if (bytes.length > 32768) {
                LOG.warn("[TC-ROD-y18-CONFIG] rejected >32KiB profile");
                return;
            }
            int hash=java.util.Arrays.hashCode(bytes);
            if (hash==Y18_HASH) return;
            java.util.Properties p=new java.util.Properties();
            try (java.io.StringReader reader=new java.io.StringReader(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8))) {
                p.load(reader);
            }
            Y18_CFG=p;
            Y18_HASH=hash;
            ++Y18_REV;
            Y18_LOGGED.clear();
            LOG.info("[TC-ROD-y18-CONFIG] revision={} profile={} keys={} enabled={} hash={}",
                    Y18_REV,Y18_GEOMETRY_PATH,p.size(),p.getProperty("y18.enabled","false"),
                    Integer.toHexString(hash));
        } catch (java.io.IOException | IllegalArgumentException ex) {
            LOG.warn("[TC-ROD-y18-CONFIG] profile reload ignored: {}", ex.toString());
        }
    }

    // DEV38 y18i conservative non-Heavy containment: phase-zero anchored
    // long-axis reciprocation. The displacement is exactly the old orbit's
    // longitudinal component, so only the visually incorrect vertical sweep
    // is removed.
    private static void slideX(
            Rig rig,
            float phase,
            float radiusPx,
            String... names) {
        float dx = ((float) Math.cos(phase) - 1.0F) * radiusPx;
        for (String name : names) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.x = base.x + dx;
            base.part.y = base.y;
            base.part.z = base.z;
        }
    }

    private static void slideZ(
            Rig rig,
            float phase,
            float radiusPx,
            String... names) {
        float dz = ((float) Math.cos(phase) - 1.0F) * radiusPx;
        for (String name : names) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.x = base.x;
            base.part.y = base.y;
            base.part.z = base.z + dz;
        }
    }

    /**
     * DEV38 y18m repair derived directly from the y18l break-apart run.
     *
     * Proven identities from y18l:
     *  - Mogul long coupling rods: part_018 / part_019 (1x1x23)
     *  - Forney long coupling rods: part_018 / part_019 (1x1x22)
     *  - Forney main drivers: part_020 / part_021 / part_024 / part_025 (1x9x9)
     *  - Forney front small wheel pair: part_000 / part_001 (1x6x6)
     *
     * The y18k failure was not bad wheel identity: it rotated the correct Forney
     * wheel cubes around ModelPart pivots located on the cube edge. This helper
     * compensates the ModelPart translation so the *cube center* stays fixed while
     * xRot changes. That makes each wheel spin in place instead of orbiting.
     *
     * No secondary 0x1x11 / 1x1x9 / 10 / 13 / 14 link is animated here. y18l
     * identified them, but did not prove which endpoints are crank vs slider.
     * They intentionally remain at authored rest for the next endpoint solver.
     */
    private static void y18mApplyMogulForney(
            String id, Object model, Rig rig, float phase) {
        boolean mogul = "locomotive_steam_mogul".equals(id);
        boolean forney = "locomotive_steam_forney".equals(id);
        if (!mogul && !forney) return;

        // y18n keeps the y18m Forney center-pivot wheel correction, but the
        // legacy linkage itself is now retired. Start from authored rest every
        // frame, hide all y18l-identified rod/valve families, then animate only
        // our own baked coupling rods + crank pins.
        rig.restore();
        Map<String, ModelPart> all = modelParts(model);
        if (all.isEmpty()) return;

        java.util.ArrayList<String> centeredDrivers = new java.util.ArrayList<>();
        java.util.ArrayList<String> centeredSmall = new java.util.ArrayList<>();
        if (forney) {
            for (String name : new String[] {"part_020", "part_021", "part_024", "part_025"}) {
                ModelPart part = all.get(name);
                if (part != null && y18mCenterSpinX(part, phase)) centeredDrivers.add(name);
            }
            for (String name : new String[] {"part_000", "part_001"}) {
                ModelPart part = all.get(name);
                if (part != null && y18mCenterSpinX(part, phase)) centeredSmall.add(name);
            }
        }

        java.util.ArrayList<String> hidden = new java.util.ArrayList<>();
        for (String name : y18nLegacyLinks(id)) {
            ModelPart part = all.get(name);
            if (part == null) continue;
            part.visible = false;
            hidden.add(name);
        }

        Y18NCustomRig custom = y18nCustomRig(model, id);
        if (custom == null) {
            if (Y18N_LOGGED.add(id + ":BUILD_FAIL")) {
                LOG.warn("[TC-y18n-CUSTOM] id={} status=NO_CUSTOM_RIG hiddenLegacy={}", id, hidden);
            }
            return;
        }

        final float crankRadiusPx = 1.80F;
        final float dy = (float) Math.sin(phase) * crankRadiusPx;
        final float dz = ((float) Math.cos(phase) - 1.0F) * crankRadiusPx;

        // All drivers on each side share one crank phase. Their crank pins
        // therefore receive the same Y/Z offset and the coupling rod remains a
        // straight rigid bar joining those pins.
        y18nPlace(custom.nearRod(), custom.nearX(), custom.restY() + dy, custom.restZ() + dz);
        y18nPlace(custom.farRod(), custom.farX(), custom.restY() + dy, custom.restZ() + dz);
        for (int i = 0; i < custom.driverZ().length; i++) {
            float z = custom.driverZ()[i] + dz;
            y18nPlace(custom.nearPins().get(i), custom.nearX(), 4.5F + dy, z);
            y18nPlace(custom.farPins().get(i), custom.farX(), 4.5F + dy, z);
        }

        if (Y18N_LOGGED.add(id)) {
            LOG.info("[TC-y18n-CUSTOM] id={} status=ACTIVE hiddenLegacy={} "
                            + "custom=COUPLING_RODS_PLUS_CRANK_PINS radiusPx={} "
                            + "nearX={} farX={} restY={} restZ={} driverZ={} "
                            + "forneyCenterSpinDrivers={} forneyCenterSpinSmall={} "
                            + "secondaryValveGear=HIDDEN_NOT_GUESSED",
                    id, hidden, crankRadiusPx, custom.nearX(), custom.farX(),
                    custom.restY(), custom.restZ(), java.util.Arrays.toString(custom.driverZ()),
                    centeredDrivers, centeredSmall);
        }
    }

    private static java.util.List<String> y18nLegacyLinks(String id) {
        if ("locomotive_steam_mogul".equals(id)) {
            return java.util.List.of(
                    "part_018", "part_019", "part_022", "part_023",
                    "part_062", "part_063", "part_064", "part_065",
                    "part_066", "part_067", "part_077", "part_078");
        }
        if ("locomotive_steam_forney".equals(id)) {
            return java.util.List.of(
                    "part_018", "part_019", "part_022",
                    "part_062", "part_063", "part_064", "part_065",
                    "part_066", "part_067", "part_077", "part_078");
        }
        return java.util.List.of();
    }

    private static void y18nPlace(ModelPart part, float x, float y, float z) {
        if (part == null) return;
        part.visible = true;
        part.x = x;
        part.y = y;
        part.z = z;
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }

    private static Y18NCustomRig y18nCustomRig(Object model, String id) {
        synchronized (Y18N_CUSTOM) {
            Y18NCustomRig cached = Y18N_CUSTOM.get(model);
            if (cached != null) return cached;
            if (!(model instanceof ModelPart liveRoot)) return null;

            boolean mogul = "locomotive_steam_mogul".equals(id);
            boolean forney = "locomotive_steam_forney".equals(id);
            if (!mogul && !forney) return null;

            // y18l proved these live wheel centers. Keep the custom bar at the
            // same side depth as the old linkage but move it 0.75 px outward so
            // it cannot disappear behind the rotating wheel face.
            float nearX = 6.25F;
            float farX = -6.25F;
            float restY = 4.5F;
            float restZ = mogul ? -1.5F : 0.0F;
            float rodLen = mogul ? 22.0F : 13.0F;
            float[] driverZ = mogul
                    ? new float[] {-11.5F, -1.5F, 8.5F}
                    : new float[] {-5.5F, 5.5F};

            try {
                MeshDefinition mesh = new MeshDefinition();
                PartDefinition root = mesh.getRoot();
                CubeDeformation none = new CubeDeformation(0.0F);
                CubeListBuilder rodCube = CubeListBuilder.create().texOffs(208, 232)
                        .addBox(-0.40F, -0.60F, -rodLen * 0.5F,
                                0.80F, 1.20F, rodLen, none);
                root.addOrReplaceChild("tc_y18n_near_rod", rodCube,
                        PartPose.ZERO);
                root.addOrReplaceChild("tc_y18n_far_rod",
                        CubeListBuilder.create().texOffs(208, 232)
                                .addBox(-0.40F, -0.60F, -rodLen * 0.5F,
                                        0.80F, 1.20F, rodLen, none),
                        PartPose.ZERO);
                for (int i = 0; i < driverZ.length; i++) {
                    root.addOrReplaceChild("tc_y18n_near_pin_" + i,
                            CubeListBuilder.create().texOffs(208, 232)
                                    .addBox(-0.45F, -0.85F, -0.85F,
                                            0.90F, 1.70F, 1.70F, none),
                            PartPose.ZERO);
                    root.addOrReplaceChild("tc_y18n_far_pin_" + i,
                            CubeListBuilder.create().texOffs(208, 232)
                                    .addBox(-0.45F, -0.85F, -0.85F,
                                            0.90F, 1.70F, 1.70F, none),
                            PartPose.ZERO);
                }

                ModelPart baked = LayerDefinition.create(mesh, 256, 256).bakeRoot();
                java.util.Map<String, ModelPart> liveChildren = y18nChildren(liveRoot);
                if (liveChildren == null) return null;

                ModelPart nearRod = baked.getChild("tc_y18n_near_rod");
                ModelPart farRod = baked.getChild("tc_y18n_far_rod");
                liveChildren.put("tc_y18n_near_rod", nearRod);
                liveChildren.put("tc_y18n_far_rod", farRod);
                java.util.ArrayList<ModelPart> nearPins = new java.util.ArrayList<>();
                java.util.ArrayList<ModelPart> farPins = new java.util.ArrayList<>();
                for (int i = 0; i < driverZ.length; i++) {
                    ModelPart np = baked.getChild("tc_y18n_near_pin_" + i);
                    ModelPart fp = baked.getChild("tc_y18n_far_pin_" + i);
                    liveChildren.put("tc_y18n_near_pin_" + i, np);
                    liveChildren.put("tc_y18n_far_pin_" + i, fp);
                    nearPins.add(np);
                    farPins.add(fp);
                }

                Y18NCustomRig built = new Y18NCustomRig(
                        nearRod, farRod, java.util.List.copyOf(nearPins),
                        java.util.List.copyOf(farPins), nearX, farX,
                        restY, restZ, driverZ);
                Y18N_CUSTOM.put(model, built);
                return built;
            } catch (RuntimeException ex) {
                LOG.warn("[TC-y18n-CUSTOM] id={} status=BUILD_EXCEPTION error={}",
                        id, ex.toString());
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, ModelPart> y18nChildren(ModelPart root) {
        Class<?> type = ModelPart.class;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!java.util.Map.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (!(value instanceof java.util.Map<?, ?> raw)) continue;
                    boolean looksLikeChildren = raw.isEmpty()
                            || raw.values().stream().allMatch(v -> v instanceof ModelPart);
                    if (!looksLikeChildren) continue;
                    java.util.LinkedHashMap<String, ModelPart> copy =
                            new java.util.LinkedHashMap<>((java.util.Map<String, ModelPart>) raw);
                    try {
                        field.set(root, copy);
                        return copy;
                    } catch (IllegalAccessException | RuntimeException replaceFailed) {
                        // Most baked roots already expose a mutable map object.
                        // Fall back to it; the caller's put() remains inside the
                        // outer build guard and will fail closed if immutable.
                        return (java.util.Map<String, ModelPart>) raw;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next map field.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /**
     * Rotate a ModelPart around the center of its actual cube bounds while keeping
     * that center at the exact authored-rest world/model coordinate.
     *
     * ModelPart rotates around its pivot. Recovered Forney wheel cubes are authored
     * from local Y/Z 0..9 or 0..6 with the pivot on a cube edge, so direct xRot
     * makes the entire wheel orbit. Translation compensation below implements:
     *   newPivot = restPivot + R(rest)*center - R(rest+phase)*center
     */
    private static boolean y18mCenterSpinX(ModelPart part, float phase) {
        Y18LBounds b = y18lBounds(part);
        if (b == null) return false;
        Y18KPose base = y18kBase(part);

        float cy = b.centerY();
        float cz = b.centerZ();
        float restA = base.xRot();
        float nowA = restA + phase;
        float restC = (float) Math.cos(restA);
        float restS = (float) Math.sin(restA);
        float nowC = (float) Math.cos(nowA);
        float nowS = (float) Math.sin(nowA);

        float restY = cy * restC - cz * restS;
        float restZ = cy * restS + cz * restC;
        float nowY = cy * nowC - cz * nowS;
        float nowZ = cy * nowS + cz * nowC;

        part.x = base.x();
        part.y = base.y() + restY - nowY;
        part.z = base.z() + restZ - nowZ;
        part.xRot = nowA;
        part.yRot = base.yRot();
        part.zRot = base.zRot();
        return true;
    }

    /**
     * DEV38 y18l Mogul/Forney break-apart visual identification.
     *
     * This is intentionally NOT a motion repair. It disables y18k wheel/rod writes
     * for these two locomotives and cycles eight exact geometry families. Each
     * family is returned to captured rest every callback; the active family gets a
     * fixed -12px Y / +4px Z offset. Logs record the real current-port part names,
     * cube bounds, pivots and authored rotations so a single video identifies which
     * pieces are wheels, coupling rods and crank/piston links without guessing.
     */
    private static void y18lBreakApartMogulForney(
            String id, Object model, Rig rig, float phase) {
        boolean mogul = "locomotive_steam_mogul".equals(id);
        boolean forney = "locomotive_steam_forney".equals(id);
        if (!mogul && !forney) return;

        // Undo any stale name-based y17/y18j offsets first.
        rig.restore();

        Map<String, ModelPart> all = modelParts(model);
        if (all.isEmpty()) return;

        // Build a unique candidate map in stable model-tree order. Every candidate
        // family is restored to its captured phase-zero pose before one family is
        // visually separated. This also prevents the Forney orbital-wheel failure.
        java.util.LinkedHashMap<String, ModelPart> unique = new java.util.LinkedHashMap<>();
        IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();
        for (Map.Entry<String, ModelPart> entry : all.entrySet()) {
            String name = entry.getKey();
            ModelPart part = entry.getValue();
            if (name == null || name.indexOf('/') >= 0 || part == null
                    || seen.put(part, Boolean.TRUE) != null) continue;
            Y18LBounds b = y18lBounds(part);
            if (b == null || y18lGroup(b, mogul) < 0) continue;
            unique.put(name, part);
            Y18KPose base = y18kBase(part);
            part.x = base.x(); part.y = base.y(); part.z = base.z();
            part.xRot = base.xRot(); part.yRot = base.yRot(); part.zRot = base.zRot();
        }

        long now = System.nanoTime();
        long start;
        synchronized (Y18L_START_NANOS) {
            Long existing = Y18L_START_NANOS.get(model);
            if (existing == null || now - existing > 30_000_000_000L) {
                existing = now;
                Y18L_START_NANOS.put(model, existing);
                Y18L_LAST_SLOT.remove(model);
            }
            start = existing;
        }
        long elapsed = Math.max(0L, now - start);
        int rawSlot = (int) (elapsed / Y18L_SLOT_NANOS);
        int slot = rawSlot < Y18L_SLOT_COUNT ? rawSlot : Y18L_SLOT_COUNT;

        java.util.ArrayList<String> active = new java.util.ArrayList<>();
        if (slot < Y18L_SLOT_COUNT) {
            for (Map.Entry<String, ModelPart> entry : unique.entrySet()) {
                Y18LBounds b = y18lBounds(entry.getValue());
                if (b == null || y18lGroup(b, mogul) != slot) continue;
                Y18KPose base = y18kBase(entry.getValue());
                entry.getValue().x = base.x();
                entry.getValue().y = base.y() - 12.0F;
                entry.getValue().z = base.z() + 4.0F;
                entry.getValue().xRot = base.xRot();
                entry.getValue().yRot = base.yRot();
                entry.getValue().zRot = base.zRot();
                active.add(entry.getKey());
            }
        }

        Integer previous;
        synchronized (Y18L_LAST_SLOT) {
            previous = Y18L_LAST_SLOT.put(model, slot);
        }
        if (previous == null || previous.intValue() != slot) {
            String group = slot < Y18L_SLOT_COUNT
                    ? y18lGroupName(slot, mogul) : "ALL_REST_FINAL";
            LOG.info("[TC-y18l-BREAK] id={} slot={}/{} group={} elapsedMs={} "
                            + "activeParts={} phase={} action={}",
                    id, slot + 1, Y18L_SLOT_COUNT + 1, group,
                    elapsed / 1_000_000L, active, phase,
                    slot < Y18L_SLOT_COUNT ? "OFFSET_Y_NEG12_Z_PLUS4_NO_ROTATION" : "RESTORE_ONLY");

            for (Map.Entry<String, ModelPart> entry : unique.entrySet()) {
                Y18LBounds b = y18lBounds(entry.getValue());
                if (b == null || slot >= Y18L_SLOT_COUNT
                        || y18lGroup(b, mogul) != slot) continue;
                Y18KPose base = y18kBase(entry.getValue());
                LOG.info("[TC-y18l-PART] id={} slot={} group={} name={} "
                                + "dimsPx=({},{},{}) cubeBounds=({},{},{})..({},{},{}) "
                                + "cubeCenter=({},{},{}) pivot=({},{},{}) restRotDeg=({},{},{})",
                        id, slot + 1, group, entry.getKey(),
                        b.dx(), b.dy(), b.dz(),
                        b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(),
                        b.centerX(), b.centerY(), b.centerZ(),
                        base.x(), base.y(), base.z(),
                        Math.toDegrees(base.xRot()), Math.toDegrees(base.yRot()),
                        Math.toDegrees(base.zRot()));
            }
        }
    }

    private static int y18lGroup(Y18LBounds b, boolean mogul) {
        float rodLen = mogul ? 23.0F : 22.0F;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), rodLen, 0.30F)) return 0;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 9F, 0.25F)
                && y18kNear(b.dz(), 9F, 0.25F)) return 1;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 6F, 0.25F)
                && y18kNear(b.dz(), 6F, 0.25F)) return 2;
        if (y18kNear(b.dx(), 0F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), 11F, 0.30F)) return 3;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), 13F, 0.30F)) return 4;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), 14F, 0.30F)) return 5;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), 10F, 0.30F)) return 6;
        if (y18kNear(b.dx(), 1F, 0.20F)
                && y18kNear(b.dy(), 1F, 0.20F)
                && y18kNear(b.dz(), 9F, 0.30F)) return 7;
        return -1;
    }

    private static String y18lGroupName(int slot, boolean mogul) {
        return switch (slot) {
            case 0 -> mogul ? "LONG_COUPLING_ROD_1x1x23" : "LONG_COUPLING_ROD_1x1x22";
            case 1 -> "DRIVER_WHEEL_1x9x9";
            case 2 -> "SMALL_WHEEL_1x6x6";
            case 3 -> "THIN_LINK_0x1x11";
            case 4 -> "LINK_1x1x13";
            case 5 -> "LINK_1x1x14";
            case 6 -> "LINK_1x1x10";
            case 7 -> "LINK_1x1x9";
            default -> "UNKNOWN";
        };
    }

    private static Y18LBounds y18lBounds(ModelPart part) {
        Object value = fieldValue(part, "cubes");
        if (!(value instanceof java.util.List<?> cubes) || cubes.isEmpty()) return null;
        float minX=Float.POSITIVE_INFINITY, minY=Float.POSITIVE_INFINITY,
                minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY, maxY=Float.NEGATIVE_INFINITY,
                maxZ=Float.NEGATIVE_INFINITY;
        for (Object cube : cubes) {
            if (cube == null) return null;
            Float a=y18Field(cube,"minX"), bb=y18Field(cube,"maxX"),
                    c=y18Field(cube,"minY"), d=y18Field(cube,"maxY"),
                    e=y18Field(cube,"minZ"), f=y18Field(cube,"maxZ");
            if (a==null || bb==null || c==null || d==null || e==null || f==null)
                return null;
            minX=Math.min(minX,a); maxX=Math.max(maxX,bb);
            minY=Math.min(minY,c); maxY=Math.max(maxY,d);
            minZ=Math.min(minZ,e); maxZ=Math.max(maxZ,f);
        }
        return new Y18LBounds(minX,minY,minZ,maxX,maxY,maxZ);
    }

    /**
     * DEV38 y18k evidence-driven Mogul/Forney repair.
     *
     * Original Traincraft source identifies both rigs as axle-X: driver wheels
     * are 1x9x9 cubes and the long mirrored coupling rods are 1x1x23 (Mogul)
     * or 1x1x22 (Forney).  The y18j runtime video proved the previous current-port
     * part-name mapping was wrong.  Therefore this pass selects by those exact
     * authored dimensions from the live ModelPart cubes instead of guessing names.
     *
     * Forney additionally has its 1x9x9 drivers and 1x6x6 front wheel pair
     * explicitly rotated about X using the same native phase.  Mogul wheel motion
     * was already visible in y18j, so y18k does not override Mogul wheels.
     */
    private static void y18kApplyMogulForney(
            String id, Object model, Rig rig, float phase) {
        boolean mogul = "locomotive_steam_mogul".equals(id);
        boolean forney = "locomotive_steam_forney".equals(id);
        if (!mogul && !forney) return;

        // y18j/y17 named selections are known-bad on these two.  Put every
        // allowlisted legacy rod candidate back at its authored base first.
        rig.restore();

        Map<String, ModelPart> all = modelParts(model);
        if (all.isEmpty()) return;

        float rodLen = mogul ? 23.0F : 22.0F;
        float radius = 1.80F;
        float dz = ((float) Math.cos(phase) - 1.0F) * radius;
        float dy = (float) Math.sin(phase) * radius;

        java.util.ArrayList<String> rods = new java.util.ArrayList<>();
        java.util.ArrayList<String> drivers = new java.util.ArrayList<>();
        java.util.ArrayList<String> smallWheels = new java.util.ArrayList<>();
        IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();

        for (Map.Entry<String, ModelPart> entry : all.entrySet()) {
            String name = entry.getKey();
            ModelPart part = entry.getValue();
            if (name == null || part == null || seen.put(part, Boolean.TRUE) != null)
                continue;

            Y18KDims d = y18kDims(part);
            if (d == null) continue;
            Y18KPose base = y18kBase(part);

            boolean longRod = y18kNear(d.dx(), 1F, 0.20F)
                    && y18kNear(d.dy(), 1F, 0.20F)
                    && y18kNear(d.dz(), rodLen, 0.30F);
            if (longRod) {
                // The actual coupling rod follows the common crank circle.
                // Preserve authored X and rotations; translate only in Y/Z.
                part.x = base.x();
                part.y = base.y() + dy;
                part.z = base.z() + dz;
                part.xRot = base.xRot();
                part.yRot = base.yRot();
                part.zRot = base.zRot();
                rods.add(name);
                continue;
            }

            if (forney) {
                boolean driver = y18kNear(d.dx(), 1F, 0.20F)
                        && y18kNear(d.dy(), 9F, 0.25F)
                        && y18kNear(d.dz(), 9F, 0.25F);
                boolean small = y18kNear(d.dx(), 1F, 0.20F)
                        && y18kNear(d.dy(), 6F, 0.25F)
                        && y18kNear(d.dz(), 6F, 0.25F);
                if (driver || small) {
                    // Axle-X wheel: rotate in the Y/Z face plane.  Assignment
                    // from captured rest prevents frame-to-frame accumulation.
                    part.x = base.x(); part.y = base.y(); part.z = base.z();
                    part.xRot = base.xRot() + phase;
                    part.yRot = base.yRot();
                    part.zRot = base.zRot();
                    (driver ? drivers : smallWheels).add(name);
                }
            }
        }

        if (Y18K_LOGGED.add(id)) {
            LOG.info("[TC-ROD-y18k-GEOMETRY] id={} plane=ZY rodLenPx={} "
                            + "rods={} forneyDrivers={} forneySmallWheels={} "
                            + "phase={} mapping=LIVE_CUBE_DIMS_NOT_PART_NAMES",
                    id, rodLen, rods, drivers, smallWheels, phase);
        }
    }

    private static Y18KPose y18kBase(ModelPart part) {
        synchronized (Y18K_BASES) {
            Y18KPose p = Y18K_BASES.get(part);
            if (p == null) {
                p = new Y18KPose(part.x, part.y, part.z,
                        part.xRot, part.yRot, part.zRot);
                Y18K_BASES.put(part, p);
            }
            return p;
        }
    }

    private static boolean y18kNear(float value, float target, float tolerance) {
        return Float.isFinite(value) && Math.abs(value - target) <= tolerance;
    }

    private static Y18KDims y18kDims(ModelPart part) {
        Object value = fieldValue(part, "cubes");
        if (!(value instanceof java.util.List<?> cubes) || cubes.isEmpty()) return null;
        float minX=Float.POSITIVE_INFINITY, minY=Float.POSITIVE_INFINITY,
                minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY, maxY=Float.NEGATIVE_INFINITY,
                maxZ=Float.NEGATIVE_INFINITY;
        for (Object cube : cubes) {
            if (cube == null) return null;
            Float a=y18Field(cube,"minX"), b=y18Field(cube,"maxX"),
                    c=y18Field(cube,"minY"), d=y18Field(cube,"maxY"),
                    e=y18Field(cube,"minZ"), f=y18Field(cube,"maxZ");
            if (a==null || b==null || c==null || d==null || e==null || f==null)
                return null;
            minX=Math.min(minX,a); maxX=Math.max(maxX,b);
            minY=Math.min(minY,c); maxY=Math.max(maxY,d);
            minZ=Math.min(minZ,e); maxZ=Math.max(maxZ,f);
        }
        return new Y18KDims(maxX-minX, maxY-minY, maxZ-minZ);
    }

    private static void orbitXY(
            Rig rig,
            float phase,
            float radiusPx,
            String... names) {

        // Exact authored resting position at phase=0.
        // y15's cos(0)*radius misplaced the rod one full crank throw at rest.
        float dx = ((float) Math.cos(phase) - 1.0F) * radiusPx;
        float dy = (float) Math.sin(phase) * radiusPx;

        for (String name : names) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.x = base.x + dx;
            base.part.y = base.y + dy;
        }
    }

    private static void orbitZY(
            Rig rig,
            float phase,
            float radiusPx,
            String... names) {

        // Same phase-zero anchoring, for axle-X wheel planes (Z/Y).
        float dz = ((float) Math.cos(phase) - 1.0F) * radiusPx;
        float dy = (float) Math.sin(phase) * radiusPx;

        for (String name : names) {
            Base base = rig.parts.get(name);
            if (base == null) continue;
            base.part.z = base.z + dz;
            base.part.y = base.y + dy;
        }
    }

    private static Rig build(String id, Object model) {
        Map<String, ModelPart> parts = modelParts(model);
        Map<String, Base> selected = new LinkedHashMap<>();

        for (String name : namesFor(id)) {
            ModelPart part = parts.get(name);
            if (part != null) selected.put(name, new Base(part));
        }

        // DEV42 evidence-backed trial masks. These are not runtime-accepted
        // removals until the player confirms the correct artifact disappeared.
        // Suppress only the named exposed leaves, leaving frozen wheel parts,
        // renderers and all other authored geometry untouched.
        List<String> hidden = new ArrayList<>();
        if ("locomotive_steam_heavy".equals(id)) {
            maskCandidate(parts, hidden, "part_022", "part_035");
        } else if ("locomotive_diesel_v60".equals(id)) {
            maskCandidate(parts, hidden, "box5", "box6", "box76", "box77");
        }
        if (!hidden.isEmpty()) {
            LOG.info("[TC-ROD-y16-MASK] id={} hidden={} movedParts={}",
                    id, hidden, selected.keySet());
        }

        return new Rig(id, selected);
    }

    private static void maskCandidate(
            Map<String, ModelPart> all,
            List<String> hidden,
            String... names) {
        for (String name : names) {
            ModelPart part = all.get(name);
            if (part == null) continue;
            part.visible = false;
            hidden.add(name);
        }
    }

    private static String[] namesFor(String id) {
        return switch (id) {
            case "locomotive_steam_heavy" -> new String[] {
                    "part_020", "part_029",
                    "part_036", "part_037"
            };
            case "locomotive_steam_mogul" -> new String[] {
                    "part_018", "part_019", "part_022", "part_023"
            };
            case "locomotive_steam_forney" -> new String[] {
                    "part_018", "part_019"
            };
            case "locomotive_steam_br01" -> new String[] {
                    "part_006", "part_007", "part_026",
                    "part_028", "part_030", "part_058"
            };
            case "locomotive_steam_ussr" -> new String[] {
                    "part_042", "part_046", "part_049",
                    "part_055", "part_071", "part_072"
            };
            case "locomotive_diesel_v60" -> new String[] {
                    "box4", "box83",
                    "tc_v60_pin_neg_0", "tc_v60_pin_neg_1", "tc_v60_pin_neg_2",
                    "tc_v60_pin_pos_0", "tc_v60_pin_pos_1", "tc_v60_pin_pos_2"
            };
            default -> new String[0];
        };
    }

    private static Object rendererModel(Object renderer) {
        Object named = fieldValue(renderer, "model");
        if (named != null) return named;

        Class<?> type = renderer.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value == null) continue;

                    if (value instanceof ModelPart
                            || !modelPartFields(value).isEmpty()) {
                        return value;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue looking.
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    /**
     * Returns exact authored ModelPart names from both Java fields and the
     * recursive ModelPart child tree.
     */
    private static Map<String, ModelPart> modelParts(Object model) {
        Map<String, ModelPart> out = new LinkedHashMap<>();
        IdentityHashMap<ModelPart, Boolean> visited = new IdentityHashMap<>();

        if (model instanceof ModelPart root) {
            collectChildren(root, "", out, visited);
            return out;
        }

        Map<String, ModelPart> direct = modelPartFields(model);
        for (Map.Entry<String, ModelPart> entry : direct.entrySet()) {
            String fieldName = entry.getKey();
            ModelPart part = entry.getValue();
            if (fieldName == null || part == null) continue;

            out.putIfAbsent(fieldName, part);
            collectChildren(part, fieldName, out, visited);
        }

        return out;
    }

    private static void collectChildren(
            ModelPart parent,
            String path,
            Map<String, ModelPart> out,
            IdentityHashMap<ModelPart, Boolean> visited) {

        if (parent == null || visited.put(parent, Boolean.TRUE) != null) return;

        Map<String, ModelPart> map = children(parent);
        if (map.isEmpty()) return;

        for (Map.Entry<String, ModelPart> entry : map.entrySet()) {
            String leaf = entry.getKey();
            ModelPart part = entry.getValue();
            if (leaf == null || part == null) continue;

            String full = path == null || path.isEmpty()
                    ? leaf
                    : path + "/" + leaf;

            // DEV42 modern ModelPart IDs use the authored leaf name.
            out.putIfAbsent(leaf, part);

            // Preserve the complete path too for diagnostics/future use.
            out.putIfAbsent(full, part);

            collectChildren(part, full, out, visited);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart root) {
        if (root == null) return Map.of();

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

                    if (good) return (Map<String, ModelPart>) raw;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try another Map field.
                }
            }

            type = type.getSuperclass();
        }

        return Map.of();
    }

    private static Object fieldValue(Object owner, String wanted) {
        if (owner == null) return null;

        Class<?> type = owner.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(wanted);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        return null;
    }

    private static Map<String, ModelPart> modelPartFields(Object model) {
        Map<String, ModelPart> out = new HashMap<>();
        if (model == null) return out;

        Class<?> type = model.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) {
                        out.putIfAbsent(field.getName(), part);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue.
                }
            }
            type = type.getSuperclass();
        }

        return out;
    }

    private static final class Rig {
        final String id;
        final Map<String, Base> parts;

        Rig(String id, Map<String, Base> parts) {
            this.id = id;
            this.parts = parts;
        }

        void restore() {
            for (Base base : parts.values()) {
                base.restore();
            }
        }
    }

    private static final class Base {
        final ModelPart part;
        final float x;
        final float y;
        final float z;
        final float xRot;
        final float yRot;
        final float zRot;

        Base(ModelPart part) {
            this.part = part;
            this.x = part.x;
            this.y = part.y;
            this.z = part.z;
            this.xRot = part.xRot;
            this.yRot = part.yRot;
            this.zRot = part.zRot;
        }

        void restore() {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
        }
    }
}