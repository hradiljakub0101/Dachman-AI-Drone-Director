package cz.dachman.drone.director;

/**
 * Deterministic virtual camera operator for DJI Mini 2.
 *
 * It predicts short worker motion, keeps intentional lead room, drives only the pitch axis of
 * the gimbal and changes digital zoom slowly. Aircraft yaw supplies horizontal framing because
 * the Mini 2 gimbal follows aircraft heading. The class has no DJI dependency and is unit tested.
 */
public final class CameraDirector {
    public static final float MIN_DIGITAL_ZOOM = 1f;
    public static final float MAX_DIGITAL_ZOOM = 2f;
    private static final float LOOK_AHEAD_SECONDS = 0.28f;
    private static final float HORIZONTAL_DEAD_ZONE = 0.028f;
    private static final float VERTICAL_DEAD_ZONE = 0.035f;
    private static final float FRAME_EDGE_MARGIN = 0.06f;

    private TargetBox previousTarget;
    private long previousObservedAt;
    private long lastZoomUpdateAt;
    private float velocityX;
    private float velocityY;
    private float smoothedGimbalPitch;
    private float zoomFactor = MIN_DIGITAL_ZOOM;
    private volatile float appliedZoomFactor = MIN_DIGITAL_ZOOM;
    private volatile boolean digitalZoomSupported;

    public void begin(FlightPlan plan, TrackingSnapshot tracking) {
        TargetBox target = target(plan, tracking);
        previousTarget = target;
        previousObservedAt = target == null ? 0L : target.observedAtMillis;
        lastZoomUpdateAt = 0L;
        velocityX = 0f;
        velocityY = 0f;
        smoothedGimbalPitch = 0f;
    }

    public CameraDirective command(FlightPlan plan, TrackingSnapshot tracking) {
        TargetBox target = target(plan, tracking);
        if (target == null || plan == null || plan.mode == FlightMode.HOLD) {
            smoothedGimbalPitch = mix(smoothedGimbalPitch, 0f, 0.45f);
            return CameraDirective.IDLE;
        }

        updateVelocity(target);
        Framing framing = framing(plan.mode);
        float predictedX = clamp(target.centerX() + velocityX * LOOK_AHEAD_SECONDS, 0f, 1f);
        float predictedY = clamp(target.centerY() + velocityY * LOOK_AHEAD_SECONDS, 0f, 1f);

        // Give a moving worker a little more room in the direction they are walking.
        float movementLead = clamp(velocityX * 0.12f, -0.075f, 0.075f);
        float desiredX = clamp(framing.centerX - movementLead, 0.34f, 0.66f);
        float horizontalError = deadZone(predictedX - desiredX, HORIZONTAL_DEAD_ZONE);
        float verticalError = deadZone(predictedY - framing.centerY, VERTICAL_DEAD_ZONE);

        float maximumPitchSpeed = pitchLimit(plan.profile);
        float requestedGimbalPitch = clamp(-verticalError * 46f,
            -maximumPitchSpeed, maximumPitchSpeed);
        float smoothing = requestedGimbalPitch == 0f ? 0.38f : 0.26f;
        smoothedGimbalPitch = mix(smoothedGimbalPitch, requestedGimbalPitch, smoothing);
        if (Math.abs(smoothedGimbalPitch) < 0.18f) smoothedGimbalPitch = 0f;

        float observedZoom = digitalZoomSupported
            ? clamp(appliedZoomFactor, MIN_DIGITAL_ZOOM, MAX_DIGITAL_ZOOM)
            : MIN_DIGITAL_ZOOM;
        float unzoomedTargetHeight = target.height() / observedZoom;
        if (digitalZoomSupported) updateZoom(target, framing);
        return new CameraDirective(horizontalError, smoothedGimbalPitch,
            digitalZoomSupported ? zoomFactor : FlightCommand.NO_DIGITAL_ZOOM,
            unzoomedTargetHeight);
    }

    public void updateAppliedZoom(boolean supported, float factor) {
        digitalZoomSupported = supported;
        appliedZoomFactor = supported
            ? clamp(factor, MIN_DIGITAL_ZOOM, MAX_DIGITAL_ZOOM) : MIN_DIGITAL_ZOOM;
        if (!supported) zoomFactor = MIN_DIGITAL_ZOOM;
    }

    /** Stops gimbal movement but preserves zoom knowledge between approved compositions. */
    public void resetMotion() {
        previousTarget = null;
        previousObservedAt = 0L;
        lastZoomUpdateAt = 0L;
        velocityX = 0f;
        velocityY = 0f;
        smoothedGimbalPitch = 0f;
    }

    float estimatedZoomFactor() { return zoomFactor; }
    float appliedZoomFactor() {
        return digitalZoomSupported ? appliedZoomFactor : MIN_DIGITAL_ZOOM;
    }

    private void updateVelocity(TargetBox target) {
        if (target.observedAtMillis <= previousObservedAt) return;
        if (previousTarget != null && previousObservedAt > 0L) {
            float seconds = (target.observedAtMillis - previousObservedAt) / 1_000f;
            if (seconds >= 0.06f && seconds <= 1.5f) {
                float measuredX = clamp((target.centerX() - previousTarget.centerX()) / seconds,
                    -0.9f, 0.9f);
                float measuredY = clamp((target.centerY() - previousTarget.centerY()) / seconds,
                    -0.9f, 0.9f);
                velocityX = mix(velocityX, measuredX, 0.36f);
                velocityY = mix(velocityY, measuredY, 0.36f);
            }
        }
        previousTarget = target;
        previousObservedAt = target.observedAtMillis;
    }

    private void updateZoom(TargetBox target, Framing framing) {
        if (target.observedAtMillis <= lastZoomUpdateAt) return;
        lastZoomUpdateAt = target.observedAtMillis;

        float span = Math.max(target.height(), target.width() * 0.62f);
        float error = framing.targetSpan - span;
        float next = zoomFactor;
        if (Math.abs(error) > 0.035f) {
            next += clamp(error * 0.34f, -0.065f, 0.065f);
        }

        boolean nearEdge = target.left < FRAME_EDGE_MARGIN || target.right > 1f - FRAME_EDGE_MARGIN
            || target.top < FRAME_EDGE_MARGIN || target.bottom > 1f - FRAME_EDGE_MARGIN;
        if (nearEdge) next = Math.min(next, zoomFactor - 0.07f);

        // Never choose a zoom step expected to crop the currently observed target.
        float currentSpan = Math.max(target.width(), target.height());
        if (currentSpan > 0.01f) {
            float edgeSafeMaximum = zoomFactor * 0.84f / currentSpan;
            next = Math.min(next, edgeSafeMaximum);
        }
        zoomFactor = clamp(next, MIN_DIGITAL_ZOOM,
            Math.min(MAX_DIGITAL_ZOOM, framing.maximumZoom));
    }

    private static TargetBox target(FlightPlan plan, TrackingSnapshot tracking) {
        return plan == null || tracking == null ? null : tracking.targetFor(plan.mode);
    }

    private static float pitchLimit(FlightProfile profile) {
        if (profile == FlightProfile.PRECISE) return 10f;
        if (profile == FlightProfile.CINEMATIC) return 18f;
        return 14f;
    }

    private static Framing framing(FlightMode mode) {
        switch (mode) {
            case STATIC_TRACK:
                return new Framing(0.50f, 0.46f, 0.44f, 2.00f);
            case FOLLOW:
                return new Framing(0.50f, 0.46f, 0.39f, 1.80f);
            case DUO_FOLLOW:
                return new Framing(0.50f, 0.47f, 0.48f, 1.45f);
            case ORBIT_LEFT:
                return new Framing(0.60f, 0.46f, 0.34f, 1.55f);
            case ORBIT_RIGHT:
                return new Framing(0.40f, 0.46f, 0.34f, 1.55f);
            case PULL_AWAY:
                return new Framing(0.50f, 0.50f, 0.28f, 1.30f);
            case REVEAL_UP:
                return new Framing(0.50f, 0.62f, 0.25f, 1.20f);
            case ROPE_MODE:
                return new Framing(0.50f, 0.49f, 0.42f, 1.50f);
            case HOLD:
            default:
                return new Framing(0.50f, 0.50f, 0.36f, 1.00f);
        }
    }

    private static float deadZone(float value, float zone) {
        if (Math.abs(value) <= zone) return 0f;
        return value > 0f ? value - zone : value + zone;
    }

    private static float mix(float first, float second, float amount) {
        return first + (second - first) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Framing {
        final float centerX;
        final float centerY;
        final float targetSpan;
        final float maximumZoom;

        Framing(float centerX, float centerY, float targetSpan, float maximumZoom) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.targetSpan = targetSpan;
            this.maximumZoom = maximumZoom;
        }
    }
}
