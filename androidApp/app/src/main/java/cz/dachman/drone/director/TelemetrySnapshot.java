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
    public final double aircraftLatitude;
    public final double aircraftLongitude;
    public final double homeLatitude;
    public final double homeLongitude;
    public final float headingDegrees;
    public final boolean homeLocationSet;

    public TelemetrySnapshot(boolean sdkRegistered, boolean connected, String productName, String flightMode,
            int batteryPercent, int satellites, float altitudeMeters, float horizontalSpeedMetersPerSecond,
            float verticalSpeedMetersPerSecond, int signalPercent, boolean motorsOn, boolean flying,
            boolean failsafe, boolean goingHome, String windLevel) {
        this(sdkRegistered, connected, productName, flightMode, batteryPercent, satellites, altitudeMeters,
            horizontalSpeedMetersPerSecond, verticalSpeedMetersPerSecond, signalPercent, motorsOn, flying,
            failsafe, goingHome, windLevel, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0f, false);
    }

    public TelemetrySnapshot(boolean sdkRegistered, boolean connected, String productName, String flightMode,
            int batteryPercent, int satellites, float altitudeMeters, float horizontalSpeedMetersPerSecond,
            float verticalSpeedMetersPerSecond, int signalPercent, boolean motorsOn, boolean flying,
            boolean failsafe, boolean goingHome, String windLevel, double aircraftLatitude,
            double aircraftLongitude, double homeLatitude, double homeLongitude, float headingDegrees,
            boolean homeLocationSet) {
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
        this.aircraftLatitude = aircraftLatitude;
        this.aircraftLongitude = aircraftLongitude;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
        this.headingDegrees = normalizeHeading(headingDegrees);
        this.homeLocationSet = homeLocationSet;
    }

    public boolean hasAircraftLocation() {
        return validCoordinate(aircraftLatitude, aircraftLongitude);
    }

    public boolean hasHomeLocation() {
        return homeLocationSet && validCoordinate(homeLatitude, homeLongitude);
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
            && latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d
            && !(Math.abs(latitude) < 0.000001d && Math.abs(longitude) < 0.000001d);
    }

    private static float normalizeHeading(float value) {
        float normalized = value % 360f;
        return normalized < 0f ? normalized + 360f : normalized;
    }

    public static TelemetrySnapshot disconnected() {
        return new TelemetrySnapshot(false, false, "Nepřipojeno", "—", -1, 0, 0f, 0f, 0f, -1,
            false, false, false, false, "UNKNOWN");
    }
}
