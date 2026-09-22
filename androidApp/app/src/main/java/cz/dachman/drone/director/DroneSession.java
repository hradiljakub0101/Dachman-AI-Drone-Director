package cz.dachman.drone.director;

import android.graphics.SurfaceTexture;

/** Platform-neutral surface used by the UI; the DJI and demo flavours implement it separately. */
public interface DroneSession {
    interface Completion { void onComplete(boolean success, String message); }
    interface Listener {
        void onStatus(String message);
        void onTelemetry(TelemetrySnapshot telemetry);
        void onVideoState(boolean active);
        void onPilotOverride(String reason);
        void onCameraState(boolean recording);
        void onCameraStorageState(CameraStorageStatus status);
        void onCameraAutomationState(boolean digitalZoomSupported, float appliedZoomFactor);
        void onAircraftAction(String action, boolean active);
        void onReturnHomeStatus(ReturnHomeStatus status);
    }

    void setListener(Listener listener);
    void connect();
    void onUsbAccessoryAttached();
    void attachVideo(SurfaceTexture texture, int width, int height);
    void detachVideo();
    boolean supportsLiveControl();
    void enableVirtualStick(Completion completion);
    default void sendCommand(FlightCommand command) { sendCommand(command, (success, message) -> {}); }
    void sendCommand(FlightCommand command, Completion completion);
    void disableVirtualStick(String reason, Completion completion);
    void toggleRecording(Completion completion);
    void takePhoto(Completion completion);
    void prepareReturnHome(int heightMeters, Completion completion);
    void prepareReturnHomeFromDevice(double latitude, double longitude, float accuracyMeters,
        int heightMeters, Completion completion);
    void startTakeoff(Completion completion);
    void startLanding(Completion completion);
    void startReturnHome(Completion completion);
    void cancelAircraftAction(Completion completion);
    void close();
}
