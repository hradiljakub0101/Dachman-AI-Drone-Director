package cz.dachman.drone.director;

/** Converts a reviewed mode and current target framing into bounded body-frame velocities. */
public final class FlightDirector {
    private float referenceTargetHeight = 0.3f;
    private FlightCommand previous = FlightCommand.ZERO;

    public void begin(FlightPlan plan, TrackingSnapshot tracking) {
        TargetBox target = tracking.targetFor(plan.mode);
        referenceTargetHeight = target == null ? 0.3f : Math.max(0.08f, target.height());
        previous = FlightCommand.ZERO;
    }

    public FlightCommand command(FlightPlan plan, TelemetrySnapshot telemetry, TrackingSnapshot tracking) {
        TargetBox target = tracking.targetFor(plan.mode);
        if (target == null) return FlightCommand.ZERO;

        FlightProfile profile = plan.profile;
        float xError = clamp(target.centerX() - 0.5f, -0.5f, 0.5f);
        float yError = clamp(target.centerY() - 0.5f, -0.5f, 0.5f);
        float sizeError = referenceTargetHeight - target.height();
        float yaw = clamp(xError * profile.maxYawDegreesPerSecond * 2.2f,
            -profile.maxYawDegreesPerSecond, profile.maxYawDegreesPerSecond);
        float gimbal = clamp(-yError * 38f, -20f, 20f);
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
        FlightCommand requested = new FlightCommand(pitch, roll, yaw, vertical, gimbal);
        previous = smooth(previous, requested, 0.22f);
        return previous;
    }

    public void reset() { previous = FlightCommand.ZERO; }

    private static FlightCommand smooth(FlightCommand old, FlightCommand requested, float alpha) {
        return new FlightCommand(
            mix(old.pitch, requested.pitch, alpha), mix(old.roll, requested.roll, alpha),
            mix(old.yaw, requested.yaw, alpha), mix(old.vertical, requested.vertical, alpha),
            mix(old.gimbalPitch, requested.gimbalPitch, alpha));
    }

    private static float mix(float a, float b, float alpha) { return a + (b - a) * alpha; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
