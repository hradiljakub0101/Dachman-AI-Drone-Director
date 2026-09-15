package cz.dachman.drone.director;

/** Low-gain geographic feed-forward. Vision remains authoritative for framing. */
public final class HybridFollowController {
    private double desiredNorth;
    private double desiredEast;
    private boolean initialized;

    public void begin(TelemetrySnapshot aircraft, WorkerPositionFix worker) {
        initialized = aircraft != null && aircraft.hasAircraftLocation() && worker != null;
        if (initialized) {
            desiredNorth = northMeters(worker.latitude, aircraft.aircraftLatitude);
            desiredEast = eastMeters(worker.latitude, worker.longitude, aircraft.aircraftLongitude);
        }
    }

    public FlightCommand blend(FlightCommand vision, FlightProfile profile, TelemetrySnapshot aircraft,
            WorkerPositionFix worker) {
        if (!initialized || aircraft == null || !aircraft.hasAircraftLocation() || worker == null) return vision;
        double currentNorth = northMeters(worker.latitude, aircraft.aircraftLatitude);
        double currentEast = eastMeters(worker.latitude, worker.longitude, aircraft.aircraftLongitude);
        double northVelocity = worker.northMetersPerSecond + (desiredNorth - currentNorth) * 0.18d;
        double eastVelocity = worker.eastMetersPerSecond + (desiredEast - currentEast) * 0.18d;
        double heading = Math.toRadians(aircraft.headingDegrees);
        float forward = (float)(northVelocity * Math.cos(heading) + eastVelocity * Math.sin(heading));
        float right = (float)(-northVelocity * Math.sin(heading) + eastVelocity * Math.cos(heading));
        float assist = profile.maxHorizontalMetersPerSecond * 0.35f;
        return new FlightCommand(
            clamp(vision.pitch + clamp(forward, -assist, assist), -profile.maxHorizontalMetersPerSecond, profile.maxHorizontalMetersPerSecond),
            clamp(vision.roll + clamp(right, -assist, assist), -profile.maxHorizontalMetersPerSecond, profile.maxHorizontalMetersPerSecond),
            vision.yaw, vision.vertical, vision.gimbalPitch, vision.digitalZoomFactor);
    }

    public void reset() { initialized = false; }
    public static double distanceMeters(double firstLat, double firstLon, double secondLat, double secondLon) {
        double north = northMeters(firstLat, secondLat);
        double east = eastMeters(firstLat, firstLon, secondLon);
        return Math.hypot(north, east);
    }
    private static double northMeters(double originLat, double targetLat) {
        return Math.toRadians(targetLat - originLat) * 6_371_000d;
    }
    private static double eastMeters(double originLat, double originLon, double targetLon) {
        return Math.toRadians(targetLon - originLon) * 6_371_000d * Math.cos(Math.toRadians(originLat));
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
