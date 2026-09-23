package cz.dachman.drone.director;

/** Home-point and optional return safety capabilities reported by the DJI controller. */
public final class ReturnHomeStatus {
    public static final double MAXIMUM_HOME_VERIFICATION_ERROR_METERS = 10d;

    public final boolean ready;
    public final boolean configuring;
    public final double latitude;
    public final double longitude;
    public final long savedAtEpochMillis;
    public final double verificationErrorMeters;
    public final double distanceToHomeMeters;
    public final int rthHeightMeters;
    public final boolean smartRthEnabled;
    public final boolean failSafeGoHome;
    public final String detail;

    private ReturnHomeStatus(boolean ready, boolean configuring, double latitude, double longitude,
            long savedAtEpochMillis, double verificationErrorMeters, double distanceToHomeMeters,
            int rthHeightMeters, boolean smartRthEnabled, boolean failSafeGoHome, String detail) {
        this.ready = ready;
        this.configuring = configuring;
        this.latitude = latitude;
        this.longitude = longitude;
        this.savedAtEpochMillis = savedAtEpochMillis;
        this.verificationErrorMeters = verificationErrorMeters;
        this.distanceToHomeMeters = distanceToHomeMeters;
        this.rthHeightMeters = rthHeightMeters;
        this.smartRthEnabled = smartRthEnabled;
        this.failSafeGoHome = failSafeGoHome;
        this.detail = detail == null ? "Návratový bod není připraven." : detail;
    }

    public static ReturnHomeStatus missing(String detail) {
        return new ReturnHomeStatus(false, false, Double.NaN, Double.NaN, 0L,
            Double.NaN, Double.NaN, 0, false, false, detail);
    }

    public static ReturnHomeStatus configuring() {
        return new ReturnHomeStatus(false, true, Double.NaN, Double.NaN, 0L,
            Double.NaN, Double.NaN, 0, false, false, "Zapisuji a ověřuji návratový bod…");
    }

    public static ReturnHomeStatus verified(double latitude, double longitude, long savedAtEpochMillis,
            double verificationErrorMeters, int rthHeightMeters, boolean smartRthEnabled,
            boolean failSafeGoHome) {
        boolean coordinateValid = validCoordinate(latitude, longitude);
        boolean accurate = Double.isFinite(verificationErrorMeters) && verificationErrorMeters >= 0d
            && verificationErrorMeters <= MAXIMUM_HOME_VERIFICATION_ERROR_METERS;
        boolean heightValid = rthHeightMeters >= 20 && rthHeightMeters <= 500;
        // The actual Home Point and RTH height are sufficient for a pilot-commanded DJI
        // startGoHome(). Smart battery RTH and link-loss GO_HOME are reported separately;
        // older aircraft firmware can reject those settings even after accepting Home.
        boolean ready = coordinateValid && accurate && heightValid;
        String detail = ready
            ? "Home Point a RTH výška ověřeny. Smart RTH: " + (smartRthEnabled ? "zapnutý" : "nedostupný/neověřený")
                + ". GO_HOME při ztrátě spojení: " + (failSafeGoHome ? "zapnuto" : "nedostupné/neověřené") + "."
            : "Home Point nebo RTH výška se nepodařilo ověřit.";
        return new ReturnHomeStatus(ready, false, latitude, longitude, savedAtEpochMillis,
            verificationErrorMeters, verificationErrorMeters, rthHeightMeters,
            smartRthEnabled, failSafeGoHome, detail);
    }

    public ReturnHomeStatus withDistanceToHome(double distanceMeters) {
        return new ReturnHomeStatus(ready, configuring, latitude, longitude, savedAtEpochMillis,
            verificationErrorMeters, distanceMeters, rthHeightMeters, smartRthEnabled,
            failSafeGoHome, detail);
    }

    /** A saved app value cannot stand in for the aircraft's current Home Point. */
    public boolean matchesAircraftHome(double aircraftHomeLatitude, double aircraftHomeLongitude) {
        return ready && validCoordinate(aircraftHomeLatitude, aircraftHomeLongitude)
            && distanceMeters(latitude, longitude, aircraftHomeLatitude, aircraftHomeLongitude)
                <= MAXIMUM_HOME_VERIFICATION_ERROR_METERS;
    }

    public static double distanceMeters(double firstLatitude, double firstLongitude,
            double secondLatitude, double secondLongitude) {
        if (!validCoordinate(firstLatitude, firstLongitude)
                || !validCoordinate(secondLatitude, secondLongitude)) return Double.NaN;
        double earthRadius = 6_371_000d;
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstRadians = Math.toRadians(firstLatitude);
        double secondRadians = Math.toRadians(secondLatitude);
        double a = Math.sin(latitudeDelta / 2d) * Math.sin(latitudeDelta / 2d)
            + Math.cos(firstRadians) * Math.cos(secondRadians)
            * Math.sin(longitudeDelta / 2d) * Math.sin(longitudeDelta / 2d);
        return earthRadius * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
            && latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d
            && !(Math.abs(latitude) < 0.000001d && Math.abs(longitude) < 0.000001d);
    }
}
