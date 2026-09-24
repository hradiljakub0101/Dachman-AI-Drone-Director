package cz.dachman.drone.director;

/** Experimental, pilot-supervised approach to the fixed DJI Home Point; never lands. */
final class HomeApproachGuidance {
    static final double STOP_DISTANCE_METERS = 8d;
    static final double MAXIMUM_START_DISTANCE_METERS = 19.5d;
    static final float MAX_SPEED_METERS_PER_SECOND = 0.35f;

    static final class Result {
        final FlightCommand command;
        final double distanceMeters;
        final String stopReason;
        final boolean arrived;

        private Result(FlightCommand command, double distanceMeters, String stopReason, boolean arrived) {
            this.command = command;
            this.distanceMeters = distanceMeters;
            this.stopReason = stopReason;
            this.arrived = arrived;
        }
        static Result stop(String reason) { return new Result(FlightCommand.ZERO, Double.NaN, reason, false); }
    }

    static Result calculate(TelemetrySnapshot t, ReturnHomeStatus saved, SafetyConfiguration config) {
        if (t == null || saved == null || !saved.ready || !t.hasHomeLocation()
                || !t.hasAircraftLocation() || !saved.matchesAircraftHome(t.homeLatitude, t.homeLongitude)
                || saved.verificationErrorMeters > 2.5d
                || ReturnHomeStatus.distanceMeters(saved.latitude, saved.longitude,
                    t.homeLatitude, t.homeLongitude) > 2.5d) {
            return Result.stop("Přiblížení zastaveno: Home Point DJI nebo živá GPS nejsou ověřené.");
        }
        if (config == null || config.flightWithoutHomeAccepted) {
            return Result.stop("Přiblížení vyžaduje ověřený Home Point, režim bez Home je nepřípustný.");
        }
        if (t.satellites < 12 || t.signalPercent < 40 || t.batteryPercent < 35) {
            return Result.stop("Přiblížení zastaveno: GNSS, spojení nebo baterie pod limitem.");
        }
        if (!Float.isFinite(t.horizontalSpeedMetersPerSecond)
                || t.horizontalSpeedMetersPerSecond > 1.5f) {
            return Result.stop("Přiblížení zastaveno: neověřená nebo příliš vysoká rychlost.");
        }
        // Home is deliberately not an exact landing target: hover well outside GNSS error.
        double north = Math.toRadians(saved.latitude - t.aircraftLatitude) * 6_371_000d;
        double east = Math.toRadians(saved.longitude - t.aircraftLongitude) * 6_371_000d
            * Math.cos(Math.toRadians(t.aircraftLatitude));
        double distance = Math.hypot(north, east);
        if (!Double.isFinite(distance)) return Result.stop("Neplatné souřadnice přiblížení.");
        if (distance <= STOP_DISTANCE_METERS) {
            return new Result(FlightCommand.ZERO, distance, "Přiblížení dokončeno: vis u Home; přistání řídí pilot.", true);
        }
        if (distance > MAXIMUM_START_DISTANCE_METERS) {
            return Result.stop("Přiblížení je dostupné pouze do 19,5 m od uloženého Home.");
        }
        SiteSafetyPlan site = config.site;
        double fraction = Math.min(1d, 2d / distance);
        double nextLatitude = t.aircraftLatitude + (saved.latitude - t.aircraftLatitude) * fraction;
        double nextLongitude = t.aircraftLongitude + (saved.longitude - t.aircraftLongitude) * fraction;
        if (!site.aircraftAllowed(t.aircraftLatitude, t.aircraftLongitude)
                || !site.aircraftAllowed(nextLatitude, nextLongitude)) {
            return Result.stop("Přiblížení zastaveno: vyznačená zakázaná zóna nebo hranice prostoru.");
        }
        float speed = Math.min(MAX_SPEED_METERS_PER_SECOND,
            (float)((distance - STOP_DISTANCE_METERS) * 0.20d));
        double heading = Math.toRadians(t.headingDegrees);
        float forward = (float)((north * Math.cos(heading) + east * Math.sin(heading)) / distance * speed);
        float right = (float)((-north * Math.sin(heading) + east * Math.cos(heading)) / distance * speed);
        return new Result(new FlightCommand(forward, right, 0f, 0f, 0f), distance, null, false);
    }

    private HomeApproachGuidance() {}
}
