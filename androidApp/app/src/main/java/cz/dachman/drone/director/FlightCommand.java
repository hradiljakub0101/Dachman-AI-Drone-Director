package cz.dachman.drone.director;

/** Body-frame velocity command plus gimbal pitch speed. */
public final class FlightCommand {
    public static final FlightCommand ZERO = new FlightCommand(0f, 0f, 0f, 0f, 0f);

    public final float pitch;
    public final float roll;
    public final float yaw;
    public final float vertical;
    public final float gimbalPitch;

    public FlightCommand(float pitch, float roll, float yaw, float vertical, float gimbalPitch) {
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.vertical = vertical;
        this.gimbalPitch = gimbalPitch;
    }

    public boolean isZero() {
        return pitch == 0f && roll == 0f && yaw == 0f && vertical == 0f && gimbalPitch == 0f;
    }
}
