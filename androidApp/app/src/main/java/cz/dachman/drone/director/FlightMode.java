package cz.dachman.drone.director;

/** Supervised map-directed manoeuvres; physical stick movement always overrides AI. */
public enum FlightMode {
    HOLD("HOLD – bez pohybu", false, false, 0),
    SURVEY_MAP("MAPOVACÍ OBLET", false, false, 90_000),
    STATIC_TRACK("STATICKÉ SLEDOVÁNÍ", true, false, 120_000),
    FOLLOW("FOLLOW – POHYB ZA CÍLEM", true, false, 60_000),
    DUO_FOLLOW("GROUP MODE", true, true, 60_000),
    ORBIT_LEFT("ORBIT VLEVO", false, false, 25_000),
    ORBIT_RIGHT("ORBIT VPRAVO", false, false, 25_000),
    PULL_AWAY("VZDÁLENÝ ODJEZD", false, false, 10_000),
    REVEAL_UP("STOUPAVÝ PRŮLET", false, false, 8_000),
    ROPE_MODE("ROPE TRASA", true, false, 60_000),
    HOME_APPROACH("TEST: PŘIBLÍŽENÍ K HOME", false, false, 45_000);

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
        return false;
    }

    public boolean requiresMapOrbitCenter() {
        return false;
    }

    /** Camera target is mandatory only for modes that explicitly follow people. */
    public boolean requiresVisualTarget() {
        return this == STATIC_TRACK || this == FOLLOW || this == DUO_FOLLOW || this == ROPE_MODE;
    }
}
