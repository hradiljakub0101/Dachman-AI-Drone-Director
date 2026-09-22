package cz.dachman.drone.director;

/** Converts a reviewed mode and current target framing into bounded body-frame velocities. */
public final class FlightDirector {
    private float referenceTargetHeight = 0.3f;
    private FlightCommand previous = FlightCommand.ZERO;
    private final CameraDirector cameraDirector = new CameraDirector();
    private int routeIndex;
    private double orbitRadiusMeters = 8d;

    public void begin(FlightPlan plan, TrackingSnapshot tracking) {
        begin(plan, TelemetrySnapshot.disconnected(), tracking, SafetyConfiguration.defaults());
    }

    public void begin(FlightPlan plan, TelemetrySnapshot telemetry, TrackingSnapshot tracking,
            SafetyConfiguration safetyConfiguration) {
        TargetBox target = plan.mode == FlightMode.SURVEY_MAP || tracking == null
            ? null : tracking.targetFor(plan.mode);
        cameraDirector.begin(plan, tracking);
        float zoom = Math.max(CameraDirector.MIN_DIGITAL_ZOOM, cameraDirector.appliedZoomFactor());
        referenceTargetHeight = target == null ? 0.3f : Math.max(0.08f, target.height() / zoom);
        previous = FlightCommand.ZERO;
        routeIndex = 0;
        SiteSafetyPlan site = safetyConfiguration == null ? SiteSafetyPlan.empty() : safetyConfiguration.site;
        SiteSafetyPlan.Point center = centroid(site.roofBoundary);
        if (center != null && telemetry.hasAircraftLocation()) orbitRadiusMeters = Math.max(5d,
            distanceMeters(telemetry.aircraftLatitude, telemetry.aircraftLongitude,
                center.latitude, center.longitude));
    }

    public FlightCommand command(FlightPlan plan, TelemetrySnapshot telemetry, TrackingSnapshot tracking) {
        return command(plan, telemetry, tracking, SafetyConfiguration.defaults());
    }

    public FlightCommand command(FlightPlan plan, TelemetrySnapshot telemetry, TrackingSnapshot tracking,
            SafetyConfiguration safetyConfiguration) {
        TargetBox target = plan.mode == FlightMode.SURVEY_MAP || tracking == null
            ? null : tracking.targetFor(plan.mode);
        if (target == null) return smoothMapCommand(plan, telemetry, safetyConfiguration);

        FlightProfile profile = plan.profile;
        CameraDirective camera = cameraDirector.command(plan, tracking);
        float xError = clamp(camera.horizontalError, -0.5f, 0.5f);
        float yError = clamp(target.centerY() - 0.5f, -0.5f, 0.5f);
        float sizeError = referenceTargetHeight - camera.unzoomedTargetHeight;
        float yaw = clamp(xError * profile.maxYawDegreesPerSecond * 2.2f,
            -profile.maxYawDegreesPerSecond, profile.maxYawDegreesPerSecond);
        float distanceCorrection = clamp(sizeError * profile.maxHorizontalMetersPerSecond * 4f,
            -profile.maxHorizontalMetersPerSecond * 0.55f,
            profile.maxHorizontalMetersPerSecond * 0.55f);

        float pitch = 0f;
        float roll = 0f;
        float vertical = 0f;
        switch (plan.mode) {
            case STATIC_TRACK:
                break;
            case FOLLOW:
            case DUO_FOLLOW:
                pitch = distanceCorrection;
                break;
            case ORBIT_LEFT:
                pitch = distanceCorrection;
                roll = -profile.maxHorizontalMetersPerSecond * 0.65f;
                break;
            case ORBIT_RIGHT:
                pitch = distanceCorrection;
                roll = profile.maxHorizontalMetersPerSecond * 0.65f;
                break;
            case PULL_AWAY:
                pitch = -profile.maxHorizontalMetersPerSecond * 0.70f;
                vertical = profile.maxVerticalMetersPerSecond * 0.18f;
                break;
            case REVEAL_UP:
                pitch = -profile.maxHorizontalMetersPerSecond * 0.35f;
                vertical = profile.maxVerticalMetersPerSecond * 0.65f;
                break;
            case ROPE_MODE:
                pitch = clamp(distanceCorrection,
                    -profile.maxHorizontalMetersPerSecond * 0.25f,
                    profile.maxHorizontalMetersPerSecond * 0.25f);
                vertical = clamp(-yError * profile.maxVerticalMetersPerSecond * 2f,
                    -profile.maxVerticalMetersPerSecond * 0.55f,
                    profile.maxVerticalMetersPerSecond * 0.55f);
                yaw = clamp(yaw, -profile.maxYawDegreesPerSecond * 0.55f,
                    profile.maxYawDegreesPerSecond * 0.55f);
                break;
            case HOLD:
            default:
                return FlightCommand.ZERO;
        }

        if (telemetry.altitudeMeters <= 2f && vertical < 0f) vertical = 0f;
        if (telemetry.altitudeMeters >= plan.level.maximumAltitudeMeters && vertical > 0f) vertical = 0f;
        FlightCommand requested = new FlightCommand(pitch, roll, yaw, vertical,
            camera.gimbalPitch, camera.digitalZoomFactor);
        previous = smooth(previous, requested, 0.22f);
        return previous;
    }

    public void updateAppliedZoom(boolean supported, float factor) {
        cameraDirector.updateAppliedZoom(supported, factor);
    }

    public void reset() {
        previous = FlightCommand.ZERO;
        cameraDirector.resetMotion();
        routeIndex = 0;
    }

    private FlightCommand smoothMapCommand(FlightPlan plan, TelemetrySnapshot telemetry,
            SafetyConfiguration configuration) {
        FlightProfile profile = plan.profile;
        SiteSafetyPlan site = configuration == null ? SiteSafetyPlan.empty() : configuration.site;
        float pitch = 0f, roll = 0f, yaw = 0f, vertical = 0f, gimbal = 0f;
        if ((plan.mode == FlightMode.FOLLOW || plan.mode == FlightMode.DUO_FOLLOW
                || plan.mode == FlightMode.ROPE_MODE)
                && telemetry.hasAircraftLocation() && site.roofBoundary.size() >= 2) {
            SiteSafetyPlan.Point waypoint = site.roofBoundary.get(routeIndex % site.roofBoundary.size());
            double distance = distanceMeters(telemetry.aircraftLatitude,
                telemetry.aircraftLongitude, waypoint.latitude, waypoint.longitude);
            if (distance < 2.5d) {
                routeIndex = (routeIndex + 1) % site.roofBoundary.size();
                waypoint = site.roofBoundary.get(routeIndex);
            }
            float scale = plan.mode == FlightMode.ROPE_MODE ? 0.35f
                : plan.mode == FlightMode.DUO_FOLLOW ? 0.50f : 0.65f;
            float[] body = bodyVelocityTo(telemetry, waypoint, profile.maxHorizontalMetersPerSecond * scale);
            pitch = body[0]; roll = body[1]; yaw = body[2];
            gimbal = plan.mode == FlightMode.ROPE_MODE ? -8f : 0f;
        } else if ((plan.mode == FlightMode.SURVEY_MAP || plan.mode == FlightMode.ORBIT_LEFT
                || plan.mode == FlightMode.ORBIT_RIGHT) && telemetry.hasAircraftLocation()) {
            SiteSafetyPlan.Point center = centroid(site.roofBoundary);
            if (center != null) {
                float[] orbit = orbitVelocity(telemetry, center, profile,
                    plan.mode == FlightMode.ORBIT_LEFT ? -1f : 1f);
                pitch = orbit[0]; roll = orbit[1]; yaw = orbit[2];
                if (plan.mode == FlightMode.SURVEY_MAP) {
                    pitch *= 0.45f; roll *= 0.45f; yaw *= 0.55f; gimbal = -12f;
                }
            } else {
                // Polygon is optional: fly a bounded local orbit while the pilot keeps visual control.
                float direction = plan.mode == FlightMode.ORBIT_LEFT ? -1f : 1f;
                roll = direction * profile.maxHorizontalMetersPerSecond * 0.45f;
                yaw = direction * profile.maxYawDegreesPerSecond * 0.35f;
                if (plan.mode == FlightMode.SURVEY_MAP) { roll *= 0.55f; yaw *= 0.55f; gimbal = -12f; }
            }
        } else if (plan.mode == FlightMode.PULL_AWAY) {
            pitch = -profile.maxHorizontalMetersPerSecond * 0.70f;
            vertical = profile.maxVerticalMetersPerSecond * 0.18f;
        } else if (plan.mode == FlightMode.REVEAL_UP) {
            pitch = -profile.maxHorizontalMetersPerSecond * 0.35f;
            vertical = profile.maxVerticalMetersPerSecond * 0.65f;
            gimbal = -6f;
        } else if (plan.mode == FlightMode.STATIC_TRACK) {
            return new FlightCommand(0f, 0f, 0f, 0f, 0f, CameraDirector.MIN_DIGITAL_ZOOM);
        } else if (plan.mode == FlightMode.HOLD) {
            return FlightCommand.ZERO;
        }
        if (telemetry.altitudeMeters >= plan.level.maximumAltitudeMeters && vertical > 0f) vertical = 0f;
        previous = smooth(previous, new FlightCommand(pitch, roll, yaw, vertical, gimbal,
            CameraDirector.MIN_DIGITAL_ZOOM), 0.22f);
        return previous;
    }

    private static float[] bodyVelocityTo(TelemetrySnapshot telemetry, SiteSafetyPlan.Point point, float speed) {
        double north = Math.toRadians(point.latitude - telemetry.aircraftLatitude) * 6_371_000d;
        double east = Math.toRadians(point.longitude - telemetry.aircraftLongitude) * 6_371_000d
            * Math.cos(Math.toRadians(telemetry.aircraftLatitude));
        double length = Math.max(0.1d, Math.hypot(north, east));
        double heading = Math.toRadians(telemetry.headingDegrees);
        float forward = (float)((north * Math.cos(heading) + east * Math.sin(heading)) / length * speed);
        float right = (float)((-north * Math.sin(heading) + east * Math.cos(heading)) / length * speed);
        float desiredHeading = (float)Math.toDegrees(Math.atan2(east, north));
        float yawError = normalize180(desiredHeading - telemetry.headingDegrees);
        return new float[]{forward, right, clamp(yawError * 0.35f, -12f, 12f)};
    }

    private float[] orbitVelocity(TelemetrySnapshot telemetry, SiteSafetyPlan.Point center,
            FlightProfile profile, float direction) {
        double north = Math.toRadians(telemetry.aircraftLatitude - center.latitude) * 6_371_000d;
        double east = Math.toRadians(telemetry.aircraftLongitude - center.longitude) * 6_371_000d
            * Math.cos(Math.toRadians(center.latitude));
        double radius = Math.max(0.5d, Math.hypot(north, east));
        double tangentNorth = -east / radius * direction;
        double tangentEast = north / radius * direction;
        double radialCorrection = clamp((float)((orbitRadiusMeters - radius) * 0.18d),
            -profile.maxHorizontalMetersPerSecond * 0.35f, profile.maxHorizontalMetersPerSecond * 0.35f);
        double velocityNorth = tangentNorth * profile.maxHorizontalMetersPerSecond * 0.55d + north / radius * radialCorrection;
        double velocityEast = tangentEast * profile.maxHorizontalMetersPerSecond * 0.55d + east / radius * radialCorrection;
        double heading = Math.toRadians(telemetry.headingDegrees);
        float forward = (float)(velocityNorth * Math.cos(heading) + velocityEast * Math.sin(heading));
        float right = (float)(-velocityNorth * Math.sin(heading) + velocityEast * Math.cos(heading));
        float faceCenter = (float)Math.toDegrees(Math.atan2(-east, -north));
        return new float[]{forward, right, clamp(normalize180(faceCenter - telemetry.headingDegrees) * 0.4f,
            -profile.maxYawDegreesPerSecond, profile.maxYawDegreesPerSecond)};
    }

    private static SiteSafetyPlan.Point centroid(java.util.List<SiteSafetyPlan.Point> points) {
        if (points == null || points.isEmpty()) return null;
        double latitude = 0d, longitude = 0d;
        for (SiteSafetyPlan.Point point : points) { latitude += point.latitude; longitude += point.longitude; }
        return new SiteSafetyPlan.Point(latitude / points.size(), longitude / points.size());
    }

    private static double distanceMeters(double latitudeA, double longitudeA,
            double latitudeB, double longitudeB) {
        double north = Math.toRadians(latitudeB - latitudeA) * 6_371_000d;
        double east = Math.toRadians(longitudeB - longitudeA) * 6_371_000d
            * Math.cos(Math.toRadians((latitudeA + latitudeB) * 0.5d));
        return Math.hypot(north, east);
    }

    private static float normalize180(float value) {
        float normalized = (value + 180f) % 360f; if (normalized < 0f) normalized += 360f;
        return normalized - 180f;
    }

    private static FlightCommand smooth(FlightCommand old, FlightCommand requested, float alpha) {
        float zoom = Float.isFinite(requested.digitalZoomFactor)
            ? requested.digitalZoomFactor : FlightCommand.NO_DIGITAL_ZOOM;
        return new FlightCommand(
            mix(old.pitch, requested.pitch, alpha), mix(old.roll, requested.roll, alpha),
            mix(old.yaw, requested.yaw, alpha), mix(old.vertical, requested.vertical, alpha),
            mix(old.gimbalPitch, requested.gimbalPitch, alpha), zoom);
    }

    private static float mix(float a, float b, float alpha) { return a + (b - a) * alpha; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
