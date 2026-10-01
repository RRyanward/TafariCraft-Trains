package traincraft.block.track;

public enum TrackMaterial {
    NORMAL("normal"),
    STEEL("steel"),
    COPPER("copper"),
    SNOW("snow"),
    ENERGY("energy");

    private final String id;

    TrackMaterial(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TrackMaterial fromId(String value) {
        if (value != null) {
            for (TrackMaterial material : values()) {
                if (material.id.equalsIgnoreCase(value)) {
                    return material;
                }
            }
        }
        return NORMAL;
    }
}
