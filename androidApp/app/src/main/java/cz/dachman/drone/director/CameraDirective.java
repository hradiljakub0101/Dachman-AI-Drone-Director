package cz.dachman.drone.director;

/** Camera framing request calculated from the selected workers and active flight mode. */
public final class CameraDirective {
    public static final CameraDirective IDLE = new CameraDirective(0f, 0f,
        FlightCommand.NO_DIGITAL_ZOOM, 0f);

    /** Normalized horizontal error used to yaw the aircraft on a fixed-yaw Mini 2 gimbal. */
    public final float horizontalError;
    /** Gimbal pitch speed in degrees per second. */
    public final float gimbalPitch;
    /** Absolute digital zoom factor, or {@link FlightCommand#NO_DIGITAL_ZOOM}. */
    public final float digitalZoomFactor;
    /** Target height with the estimated digital zoom removed, for aircraft distance control. */
    public final float unzoomedTargetHeight;

    public CameraDirective(float horizontalError, float gimbalPitch,
            float digitalZoomFactor, float unzoomedTargetHeight) {
        this.horizontalError = horizontalError;
        this.gimbalPitch = gimbalPitch;
        this.digitalZoomFactor = digitalZoomFactor;
        this.unzoomedTargetHeight = unzoomedTargetHeight;
    }
}
