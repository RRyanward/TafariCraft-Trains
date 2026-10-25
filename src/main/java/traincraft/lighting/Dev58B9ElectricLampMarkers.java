package traincraft.lighting;

/**
 * DEV58B9: HTML mesh-viewer-selected front face positions, 16 model pixels/block.
 * Verified against exact frozen RecoveredModelVL10 and RecoveredModelLocoHighSpeedZeroED
 * Java source SHA256s. This class is safe for client and dedicated server use;
 * it has no renderer references. Positions and ownership are not tunable defaults.
 */
public final class Dev58B9ElectricLampMarkers {
    private Dev58B9ElectricLampMarkers() {}

    public static boolean supported(String variant) {
        return "locomotive_electric_vl10".equals(variant)
            || "locomotive_electric_high_speed_zeroed".equals(variant);
    }

    public static double[][] front(String variant) {
        return switch (variant) {
            case "locomotive_electric_vl10" -> new double[][] { {-1.687500D, 1.055856D, 0.347852D}, {-1.687500D, 1.060198D, -0.352602D} };
            case "locomotive_electric_high_speed_zeroed" -> new double[][] { {-2.250000D, 0.995454D, -0.004453D} };
            default -> new double[0][];
        };
    }

    /** Renderer transforms from exact TCClientEvents registration. */
    public static double tx(String variant) {
        return "locomotive_electric_vl10".equals(variant) ? -1.5D : -1.7D;
    }
    public static double ty(String variant) {
        return "locomotive_electric_vl10".equals(variant) ? -0.47D : -0.44D;
    }
}
