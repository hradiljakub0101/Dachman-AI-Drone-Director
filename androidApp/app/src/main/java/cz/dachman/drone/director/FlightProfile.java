package cz.dachman.drone.director;

/** Deliberately limited profiles for worksite filming near structures. */
public enum FlightProfile {
    PRECISE("PŘESNÝ", 0.35f, 0.22f, 8f),
    STANDARD("STANDARDNÍ", 0.70f, 0.40f, 15f),
    CINEMATIC("FILMOVÝ", 1.10f, 0.60f, 24f);

    public final String label;
    public final float maxHorizontalMetersPerSecond;
    public final float maxVerticalMetersPerSecond;
    public final float maxYawDegreesPerSecond;

    FlightProfile(String label, float horizontal, float vertical, float yaw) {
        this.label = label;
        this.maxHorizontalMetersPerSecond = horizontal;
        this.maxVerticalMetersPerSecond = vertical;
        this.maxYawDegreesPerSecond = yaw;
    }

    @Override public String toString() { return label; }
}
