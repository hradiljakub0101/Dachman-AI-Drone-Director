package cz.dachman.drone.director;

/** Converts a reviewed mode and current target framing into bounded body-frame velocities. */
public final class FlightDirector {
    private float referenceTargetHeight = 0.3f;
    private FlightCommand previous = FlightCommand.ZERO;
    private final CameraDirector cameraDirector = new CameraDirector();

    public void begin(FlightPlan plan, TrackingSnapshot tracking) {
        TargetBox target = tracking.targetFor(plan.mode);
        cameraDirector.begin(plan, tracking);
        float zoom = Math.max(CameraDirector.MIN_DIGITAL_ZOOM, cameraDirector.appliedZoomFactor());
        referenceTargetHeight = target == null ? 0.3f : Math.max(0.08f, target.height() / zoom);
        previous = FlightCommand.ZERO;
    }

    public FlightCommand command(FlightPlan plan, TelemetrySnapshot telemetry, TrackingSnapshot tracking) {
        TargetBox target = tracking.targetFor(plan.mode);
        if (target == null) return FlightCommand.ZERO;

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
