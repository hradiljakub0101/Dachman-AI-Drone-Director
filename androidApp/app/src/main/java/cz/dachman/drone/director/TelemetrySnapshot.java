package cz.dachman.drone.director;

public final class TelemetrySnapshot {
    public final boolean sdkRegistered;
    public final boolean connected;
    public final String productName;
    public final String flightMode;
    public final int batteryPercent;
    public final int satellites;
    public final float altitudeMeters;
    public final float horizontalSpeedMetersPerSecond;
    public final float verticalSpeedMetersPerSecond;
    public final int signalPercent;
    public final boolean motorsOn;
    public final boolean flying;
    public final boolean failsafe;
    public final boolean goingHome;
    public final String windLevel;

    public TelemetrySnapshot(boolean sdkRegistered, boolean connected, String productName, String flightMode,
            int batteryPercent, int satellites, float altitudeMeters, float horizontalSpeedMetersPerSecond,
            float verticalSpeedMetersPerSecond, int signalPercent, boolean motorsOn, boolean flying,
            boolean failsafe, boolean goingHome, String windLevel) {
        this.sdkRegistered = sdkRegistered;
        this.connected = connected;
        this.productName = productName == null ? "—" : productName;
        this.flightMode = flightMode == null ? "—" : flightMode;
        this.batteryPercent = batteryPercent;
        this.satellites = satellites;
        this.altitudeMeters = altitudeMeters;
        this.horizontalSpeedMetersPerSecond = horizontalSpeedMetersPerSecond;
        this.verticalSpeedMetersPerSecond = verticalSpeedMetersPerSecond;
        this.signalPercent = signalPercent;
        this.motorsOn = motorsOn;
        this.flying = flying;
        this.failsafe = failsafe;
        this.goingHome = goingHome;
        this.windLevel = windLevel == null ? "UNKNOWN" : windLevel;
    }

    public static TelemetrySnapshot disconnected() {
        return new TelemetrySnapshot(false, false, "Nepřipojeno", "—", -1, 0, 0f, 0f, 0f, -1,
            false, false, false, false, "UNKNOWN");
    }
}
