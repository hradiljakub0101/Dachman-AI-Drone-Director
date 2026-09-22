package cz.dachman.drone.director;

import android.app.Activity;
import android.graphics.SurfaceTexture;

/** Offline UI flavour used by CI; the installable DJI flavour owns all aircraft access. */
final class DjiConnection implements DroneSession {
    private Listener listener;

    DjiConnection(Activity activity) {}

    @Override public void setListener(Listener listener) { this.listener = listener; }

    @Override public void connect() {
        if (listener != null) {
            listener.onStatus("Offline náhled. Nainstaluj DJI variantu pro připojení Mini 2.");
            listener.onTelemetry(TelemetrySnapshot.disconnected());
            listener.onVideoState(false);
            listener.onCameraState(false);
            listener.onCameraStorageState(CameraStorageStatus.disconnected());
            listener.onCameraAutomationState(false, CameraDirector.MIN_DIGITAL_ZOOM);
            listener.onAircraftAction("ŽÁDNÁ", false);
            listener.onReturnHomeStatus(ReturnHomeStatus.missing("DJI varianta není připojena."));
        }
    }

    @Override public void onUsbAccessoryAttached() {
        connect();
    }

    @Override public void attachVideo(SurfaceTexture texture, int width, int height) {
        if (listener != null) listener.onVideoState(false);
    }

    @Override public void detachVideo() {}
    @Override public boolean supportsLiveControl() { return false; }

    @Override public void enableVirtualStick(Completion completion) {
        completion.onComplete(false, "Offline náhled nemůže řídit dron.");
    }

    @Override public void sendCommand(FlightCommand command, Completion completion) {
        completion.onComplete(false, "Demo nemá připojený letový kontrolér.");
    }

    @Override public void disableVirtualStick(String reason, Completion completion) {
        completion.onComplete(true, reason);
    }

    @Override public void toggleRecording(Completion completion) {
        completion.onComplete(false, "Kamera není v offline náhledu připojena.");
    }

    @Override public void takePhoto(Completion completion) {
        completion.onComplete(false, "Kamera není v offline náhledu připojena.");
    }

    @Override public void prepareReturnHome(int heightMeters, Completion completion) {
        completion.onComplete(false, "Návratový bod vyžaduje DJI variantu a připojený Mini 2.");
    }

    @Override public void prepareReturnHomeFromDevice(double latitude, double longitude,
            float accuracyMeters, int heightMeters, Completion completion) {
        completion.onComplete(false, "Telefonní Home Point je dostupný pouze v DJI sestavení.");
    }

    @Override public void startTakeoff(Completion completion) {
        completion.onComplete(false, "Vzlet vyžaduje DJI variantu a připojený Mini 2.");
    }

    @Override public void startLanding(Completion completion) {
        completion.onComplete(false, "Přistání vyžaduje DJI variantu a připojený Mini 2.");
    }

    @Override public void startReturnHome(Completion completion) {
        completion.onComplete(false, "Návrat domů vyžaduje DJI variantu a připojený Mini 2.");
    }

    @Override public void cancelAircraftAction(Completion completion) {
        completion.onComplete(false, "Neprobíhá žádná akce dronu.");
    }

    @Override public void close() { listener = null; }
}
