package cz.dachman.drone.director;

/** Body-frame velocity command plus synchronized autonomous camera controls. */
public final class FlightCommand {
    public static final float NO_DIGITAL_ZOOM = Float.NaN;
    public static final FlightCommand ZERO = new FlightCommand(0f, 0f, 0f, 0f, 0f,
        NO_DIGITAL_ZOOM);

    public final float pitch;
    public final float roll;
    public final float yaw;
    public final float vertical;
    public final float gimbalPitch;
    public final float digitalZoomFactor;

    public FlightCommand(float pitch, float roll, float yaw, float vertical, float gimbalPitch) {
        this(pitch, roll, yaw, vertical, gimbalPitch, NO_DIGITAL_ZOOM);
    }

    public FlightCommand(float pitch, float roll, float yaw, float vertical, float gimbalPitch,
            float digitalZoomFactor) {
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.vertical = vertical;
        this.gimbalPitch = gimbalPitch;
        this.digitalZoomFactor = digitalZoomFactor;
    }

    public boolean isZero() {
        return pitch == 0f && roll == 0f && yaw == 0f && vertical == 0f && gimbalPitch == 0f
            && !Float.isFinite(digitalZoomFactor);
    }
}
