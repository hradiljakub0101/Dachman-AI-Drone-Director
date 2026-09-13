package cz.dachman.drone.director;

/** User-selected height ceiling relative to take-off; it is never a commanded target altitude. */
public final class FlightLevel {
    public static final int MINIMUM_METERS = 3;
    public static final int MAXIMUM_METERS = 120;
    public static final FlightLevel DETAIL = new FlightLevel(4);
    public static final FlightLevel ROOF = new FlightLevel(8);
    public static final FlightLevel WORKSITE = new FlightLevel(15);
    public static final FlightLevel WIDE = new FlightLevel(30);

    public final String label;
    public final float maximumAltitudeMeters;

    private FlightLevel(int maximumAltitudeMeters) {
        int bounded = Math.max(MINIMUM_METERS, Math.min(MAXIMUM_METERS, maximumAltitudeMeters));
        this.maximumAltitudeMeters = bounded;
        this.label = "VÝŠKOVÝ LIMIT – " + bounded + " m";
    }

    public static FlightLevel ofMeters(int meters) { return new FlightLevel(meters); }
    public int meters() { return Math.round(maximumAltitudeMeters); }

    @Override public String toString() { return label; }

    @Override public boolean equals(Object other) {
        return other instanceof FlightLevel && ((FlightLevel) other).meters() == meters();
    }

    @Override public int hashCode() { return meters(); }
}
