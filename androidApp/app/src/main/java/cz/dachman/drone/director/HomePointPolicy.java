package cz.dachman.drone.director;

/** Phone coordinates are a proposed destination, never proof of aircraft localization. */
public final class HomePointPolicy {
    public static final float MAX_PHONE_ACCURACY_METERS = 10f;
    public static final long MAX_PHONE_AGE_MILLIS = 10_000L;
    private HomePointPolicy() {}

    public static String aircraftBlock(TelemetrySnapshot telemetry) {
        if (!telemetry.connected) return "Dron není připojen.";
        if (telemetry.motorsOn || telemetry.flying) return "Home Point ukládej před spuštěním motorů.";
        if (!telemetry.hasAircraftLocation()) {
            return "Dron nemá platnou GNSS polohu (satelity: " + telemetry.satellites
                + "). Přenes jej na volné prostranství. Přesná poloha telefonu GNSS dronu nenahrazuje.";
        }
        return null;
    }

    public static String phoneBlock(TelemetrySnapshot telemetry, double latitude, double longitude,
            float accuracyMeters, long ageMillis) {
        String blocked = aircraftBlock(telemetry);
        if (blocked != null) return blocked;
        if (ageMillis < 0 || ageMillis > MAX_PHONE_AGE_MILLIS) return "Poloha telefonu je zastaralá. Vyžádej novou polohu.";
        if (!Float.isFinite(accuracyMeters) || accuracyMeters < 0f
                || accuracyMeters > MAX_PHONE_ACCURACY_METERS) {
            return "Poloha telefonu musí mít skutečnou přesnost do deseti metrů; samotné oprávnění nestačí.";
        }
        double distance = ReturnHomeStatus.distanceMeters(telemetry.aircraftLatitude,
            telemetry.aircraftLongitude, latitude, longitude);
        if (!Double.isFinite(distance) || distance > 30d) {
            return "Pro bod vzletu stůj s telefonem vedle dronu, nejvýše třicet metrů od něj.";
        }
        return null;
    }
}
