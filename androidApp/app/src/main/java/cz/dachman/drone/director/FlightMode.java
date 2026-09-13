package cz.dachman.drone.director;

/** Supported supervised camera manoeuvres. Take-off, landing and RTH stay on the RC. */
public enum FlightMode {
    HOLD("HOLD – bez pohybu", false, false, 0),
    STATIC_TRACK("STATICKÉ SLEDOVÁNÍ", true, false, 120_000),
    FOLLOW("FOLLOW – jeden pracovník", true, false, 60_000),
    DUO_FOLLOW("DUO FOLLOW – dva pracovníci", true, true, 60_000),
    ORBIT_LEFT("OBLET VLEVO", true, false, 25_000),
    ORBIT_RIGHT("OBLET VPRAVO", true, false, 25_000),
    PULL_AWAY("ODJEZD OD CÍLE", true, false, 10_000),
    REVEAL_UP("STOUPAVÉ ODHALENÍ", true, false, 8_000),
    ROPE_MODE("REŽIM LANA", true, false, 60_000);

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
}
