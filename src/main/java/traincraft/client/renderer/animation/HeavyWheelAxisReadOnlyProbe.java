package traincraft.client.renderer.animation;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.model.geom.ModelPart;

/**
 * TC_DEV38_R1G1Z7Y18E_READ_ONLY_AXLE_BASIS_PIN_REFERENCE
 *
 * Heavy Steam-only read-only diagnostic on the already measured y18d wheels.
 * Neither this class nor its single caller mutate a ModelPart, phase, world,
 * graphics pose stack, GPU data or renderer. Parent ModelPart wheel faces are
 * sampled; independently baked/overlay wheel visuals are NOT sampled here.
 * Rest-anchored pin coordinates are reference hypotheses, NOT real crankpins.
 */
public final class HeavyWheelAxisReadOnlyProbe {
    private static final org.slf4j.Logger LOG =
            com.mojang.logging.LogUtils.getLogger();
    private static final Path CONFIG = Path.of("config", "tc_dev38_y18e_axis_probe.properties");
    private static volatile boolean enabled;
    private static volatile long nextConfigNanos;
    private static volatile int configHash = Integer.MIN_VALUE;
    private static volatile boolean loggedConfigError;
    private static boolean warnedModelPartOnly;
    private static int sampleCount;

    private HeavyWheelAxisReadOnlyProbe() { }

    /** Invoked AFTER y18d rod pose and the original y18b read-only probe. */
    public static void observe(Map<String, ModelPart> all, float phase, int frame) {
        try {
            reload();
            if (!enabled || all == null || frame < 1 || frame % 90 != 0 && frame != 1) return;
            if (++sampleCount > 300) return;
            if (!warnedModelPartOnly) {
                warnedModelPartOnly = true;
                LOG.info("[TC-y18e-SCOPE] readOnly=true target=locomotive_steam_heavy "
                        + "wheelData=MODELPART_CANDIDATES_ONLY "
                        + "bakedWheelFaceOverlay=NOT_SAMPLED "
                        + "crankpin=UNPROVEN_REST_ANCHOR noPoseWrites=true");
            }
            captureWheel(all, "part_014", phase, frame, "POS_DRIVE");
            captureWheel(all, "part_034", phase, frame, "NEG_DRIVE");
            captureWheel(all, "part_051", phase, frame, "POS_LEADING");
            captureWheel(all, "part_002", phase, frame, "NEG_LEADING");
            captureRod(all, "part_020", "part_014", phase, frame, "POS");
            captureRod(all, "part_037", "part_034", phase, frame, "NEG");
        } catch (RuntimeException ex) {
            if (!loggedConfigError) {
                loggedConfigError = true;
                LOG.info("[TC-y18e-ERROR] readOnly=true cause={}",
                        ex.getClass().getSimpleName());
            }
        }
    }

    private static void reload() {
        long now = System.nanoTime();
        if (now < nextConfigNanos) return;
        nextConfigNanos = now + 1_000_000_000L;
        try {
            byte[] raw = Files.isRegularFile(CONFIG) ? Files.readAllBytes(CONFIG) : new byte[0];
            if (raw.length > 128) { invalid("CONFIG_TOO_LARGE"); return; }
            int hash = java.util.Arrays.hashCode(raw);
            if (hash == configHash) return;
            Properties props = new Properties();
            try (java.io.StringReader r = new java.io.StringReader(
                    new String(raw, StandardCharsets.UTF_8))) {
                props.load(r);
            }
            if (props.size() != 1 || !props.containsKey("enabled")) {
                invalid("MISSING_OR_UNKNOWN_KEYS"); return;
            }
            String value = props.getProperty("enabled", "").trim();
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                invalid("BAD_ENABLED_VALUE"); return;
            }
            enabled = Boolean.parseBoolean(value);
            configHash = hash;
            loggedConfigError = false;
            LOG.info("[TC-y18e-CONFIG] enabled={} sampleEveryRenderCallbacks=90 maxSamples=300 "
                    + "readOnly=true", enabled);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            invalid("CONFIG_READ_ERROR");
        }
    }

    private static void invalid(String reason) {
        enabled = false; // this controls logging only, never a model pose
        if (!loggedConfigError) {
            loggedConfigError = true;
            LOG.info("[TC-y18e-GUARD] reason={} diagnosticsDisabled=true modelUnchanged=true", reason);
        }
    }

    /** Layout in the immediate parent ModelPart's pixel frame. */
    private record Bounds(float minX, float maxX, float minY,
                          float maxY, float minZ, float maxZ) {
        float x(){return (minX+maxX)*0.5F;}
        float y(){return (minY+maxY)*0.5F;}
        float z(){return (minZ+maxZ)*0.5F;}
        float width(){return maxX-minX;}
        float height(){return maxY-minY;}
        float depth(){return maxZ-minZ;}
    }

    private static Bounds bounds(ModelPart part) {
        Object value = getField(part, "cubes");
        if (!(value instanceof java.util.List<?> cubes) || cubes.isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
              minZ = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
              maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Object cube : cubes) {
            Number a = (Number)getField(cube,"minX"), b = (Number)getField(cube,"maxX"),
                   c = (Number)getField(cube,"minY"), d = (Number)getField(cube,"maxY"),
                   e = (Number)getField(cube,"minZ"), f = (Number)getField(cube,"maxZ");
            if (a==null || b==null || c==null || d==null || e==null || f==null) return null;
            minX = Math.min(minX,a.floatValue()); maxX=Math.max(maxX,b.floatValue());
            minY = Math.min(minY,c.floatValue()); maxY=Math.max(maxY,d.floatValue());
            minZ = Math.min(minZ,e.floatValue()); maxZ=Math.max(maxZ,f.floatValue());
        }
        return Float.isFinite(minX) && Float.isFinite(maxZ)
                ? new Bounds(minX,maxX,minY,maxY,minZ,maxZ) : null;
    }

    private static Object getField(Object owner, String name) {
        if (owner == null) return null;
        Class<?> cls=owner.getClass();
        while (cls!=null && cls!=Object.class) {
            try {
                Field field=cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ex) { cls=cls.getSuperclass(); }
            catch (ReflectiveOperationException | RuntimeException ex) { return null; }
        }
        return null;
    }

    private static void captureWheel(Map<String,ModelPart> all, String name,
                                      float phase, int frame, String side) {
        ModelPart part = all.get(name);
        if (part == null) {
            if (frame==1) LOG.info("[TC-y18e-WHEEL-MISSING] name={} side={}",name,side);
            return;
        }
        Bounds b = bounds(part);
        if (b == null) {
            if (frame==1) LOG.info("[TC-y18e-WHEEL-NO-BOUNDS] name={} side={}",name,side);
            return;
        }
        double xr=part.xRot, yr=part.yRot, zr=part.zRot;
        // Minecraft ModelPart rotates local geometry Rz * Ry * Rx. Apply
        // that matrix to unit face normal Z; rotation IN the XY wheel plane
        // changes zRot, not its face normal or axle direction.
        double nx = Math.cos(zr)*Math.sin(yr)*Math.cos(xr) + Math.sin(zr)*Math.sin(xr);
        double ny = Math.sin(zr)*Math.sin(yr)*Math.cos(xr) - Math.cos(zr)*Math.sin(xr);
        double nz = Math.cos(yr)*Math.cos(xr);
        double tilt = Math.toDegrees(Math.acos(Math.min(1,Math.abs(nz))));
        double centerX = part.x + Math.cos(zr)*b.x() - Math.sin(zr)*b.y();
        double centerY = part.y + Math.sin(zr)*b.x() + Math.cos(zr)*b.y();
        double centerZ = part.z + b.z();
        boolean staticTreeWheelDuringMotion = Math.abs(phase)>0.15 && Math.abs(zr)<0.001;
        LOG.info("[TC-y18e-WHEEL] frame={} phase={} name={} side={} "
                + "pivotPx=({},{},{}) centerXYpx=({},{}) centerZpx={} "
                + "dimsPx=({},{},{}) xyzRotDeg=({},{},{}) "
                + "faceNormal=({},{},{}) faceTiltDeg={} "
                + "treeWheelUnrotatedDuringPhase={} overlaySampled=false",
                frame,phase,name,side,part.x,part.y,part.z,centerX,centerY,centerZ,
                b.width(),b.height(),b.depth(),
                Math.toDegrees(xr),Math.toDegrees(yr),Math.toDegrees(zr),
                nx,ny,nz,tilt,staticTreeWheelDuringMotion);
    }

    private static void captureRod(Map<String,ModelPart> all,String rodName,
                                   String wheelName,float phase,int frame,String side) {
        ModelPart rod=all.get(rodName),wheel=all.get(wheelName);
        if (rod==null || wheel==null) return;
        Bounds rb=bounds(rod), wb=bounds(wheel);
        if (rb==null || wb==null) return;
        if (Math.abs(rod.xRot)>0.001 || Math.abs(rod.yRot)>0.001
                || Math.abs(wheel.xRot)>0.001 || Math.abs(wheel.yRot)>0.001) {
            LOG.info("[TC-y18e-PIN-SKIP] frame={} side={} reason=NON_XY_ROTATION",frame,side);
            return;
        }
        double rz=rod.zRot, wz=wheel.zRot;
        double rodPlusX = rod.x + Math.cos(rz)*rb.maxX - Math.sin(rz)*rb.y();
        double rodPlusY = rod.y + Math.sin(rz)*rb.maxX + Math.cos(rz)*rb.y();
        double hubX = wheel.x + Math.cos(wz)*wb.x() - Math.sin(wz)*wb.y();
        double hubY = wheel.y + Math.sin(wz)*wb.x() + Math.cos(wz)*wb.y();
        // Recorded y18b geometry at phase=0. This is a rest-anchored rod tip,
        // NOT an observed crankpin on the baked wheel overlay.
        final double restDx = -18.021149 - (-16.5);
        final double restDy = 4.8943834 - 4.5;
        double modelWheelRefX = hubX + restDx*Math.cos(wz)-restDy*Math.sin(wz);
        double modelWheelRefY = hubY + restDx*Math.sin(wz)+restDy*Math.cos(wz);
        double modelWheelGap = Math.hypot(rodPlusX-modelWheelRefX,rodPlusY-modelWheelRefY);
        double phaseX = hubX + restDx*Math.cos(phase)-restDy*Math.sin(phase);
        double phaseY = hubY + restDx*Math.sin(phase)+restDy*Math.cos(phase);
        double phaseGap = Math.hypot(rodPlusX-phaseX,rodPlusY-phaseY);
        double radial = Math.hypot(rodPlusX-hubX,rodPlusY-hubY);
        LOG.info("[TC-y18e-PIN] frame={} phase={} side={} rod={} wheel={} "
                + "rodTipXYpx=({},{}) wheelCenterXYpx=({},{}) "
                + "modelWheelRotatedRestRefXYpx=({},{}) modelWheelRefGapPx={} "
                + "phaseOnlyRestRefXYpx=({},{}) phaseOnlyGapPx={} "
                + "tipRadiusPx={} rodZpx={} wheelZpx={} provenance=HYPOTHESIS_NOT_VISIBLE_PIN",
                frame,phase,side,rodName,wheelName,
                rodPlusX,rodPlusY,hubX,hubY,
                modelWheelRefX,modelWheelRefY,modelWheelGap,
                phaseX,phaseY,phaseGap,radial,
                rod.z+rb.z(),wheel.z+wb.z());
    }
}
