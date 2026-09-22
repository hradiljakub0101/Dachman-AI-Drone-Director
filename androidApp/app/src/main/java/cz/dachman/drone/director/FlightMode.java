package cz.dachman.drone.director;

/** Supervised map-directed manoeuvres; physical stick movement always overrides AI. */
public enum FlightMode {
    HOLD("HOLD – bez pohybu", false, false, 0),
    STATIC_TRACK("STATICKÝ ZÁBĚR", false, false, 120_000),
    FOLLOW("FOLLOW TRASA", false, false, 60_000),
    DUO_FOLLOW("GROUP MODE", false, false, 60_000),
    ORBIT_LEFT("ORBIT VLEVO", false, false, 25_000),
    ORBIT_RIGHT("ORBIT VPRAVO", false, false, 25_000),
    PULL_AWAY("VZDÁLENÝ ODJEZD", false, false, 10_000),
    REVEAL_UP("STOUPAVÝ PRŮLET", false, false, 8_000),
    ROPE_MODE("ROPE TRASA", false, false, 60_000);

    public final String label;
    public final boolean requiresPrimary;
    public final boolean requiresSecondary;
    public final long maxDurationMillis;

    FlightMode(String label, boolean requiresPrimary, boolean requiresSecondary, long maxDurationMillis) {
        this.label = label;
        this.requiresPrimary = requiresPrimary;
        this.requiresSecondary = requiresSecondary;
        this.maxDurationMillis = maxDurationMillis;
    }

    @Override public String toString() { return label; }

    public boolean requiresMapRoute() {
        return this == FOLLOW || this == DUO_FOLLOW || this == ROPE_MODE;
    }

    public boolean requiresMapOrbitCenter() { return this == ORBIT_LEFT || this == ORBIT_RIGHT; }
}
