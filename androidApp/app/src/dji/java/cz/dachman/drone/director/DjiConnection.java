package cz.dachman.drone.director;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import dji.common.Stick;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.StorageState;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.ConnectionFailSafeBehavior;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.SmartRTHState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.product.Model;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.sdk.airlink.AirLink;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.RemoteController;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

/** DJI Mini 2 bridge with supervised Virtual Stick and confirmed aircraft actions. */
final class DjiConnection implements DroneSession {
    static final int PERMISSION_REQUEST = 2107;
    private static final int RC_DEAD_ZONE = 80;
    private static final int GIMBAL_DIAL_DEAD_ZONE = 18;
    private static final long GIMBAL_INTERVAL_MILLIS = 180L;
    private static final long ZOOM_INTERVAL_MILLIS = 400L;
    private static final float ZOOM_EPSILON = 0.025f;
    private static final long CONNECTION_RETRY_MILLIS = 1_500L;
    private static final int MAX_CONNECTION_ATTEMPTS = 20;
    private static final long VIDEO_WATCHDOG_MILLIS = 2_500L;
    private static final long VIDEO_STALE_MILLIS = 5_000L;
    private static final int MAX_VIDEO_RESTARTS = 3;
    private static final int AUTOMATIC_RTH_BATTERY_PERCENT = 25;
    private long lastVirtualStickErrorAt;

    private enum AircraftAction {
        NONE("ŽÁDNÁ"), TAKEOFF("AUTONOMNÍ VZLET"), LANDING("AUTONOMNÍ PŘISTÁNÍ"), RETURN_HOME("NÁVRAT DOMŮ");
        final String label;
        AircraftAction(String label) { this.label = label; }
    }

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean registering;
    private boolean registered;
    private boolean closed;
    private volatile boolean virtualStickEnabled;
    private volatile boolean virtualStickEnabling;
    private volatile boolean manualDeflection;
    private volatile long virtualStickRevision;
    private volatile boolean pilotOverridePending;
    private volatile boolean recording;
    /** Prevent overlapping start/stop requests while DJI completes the prior command. */
    private boolean recordingCommandPending;
    private volatile CameraStorageStatus cameraStorageStatus = CameraStorageStatus.disconnected();
    private volatile boolean videoActive;
    private boolean compatibleModel;
    private boolean djiUsbVisible;
    private int connectionAttempts;
    private int batteryPercent = -1;
    private int signalPercent = -1;
    private volatile FlightControllerState lastFlightState;
    private volatile long lastFlightStateAt;
    private Aircraft aircraft;
    private FlightController flightController;
    private RemoteController remoteController;
    private Battery battery;
    private Camera camera;
    private Gimbal gimbal;
    private AirLink airLink;
    private VideoFeeder.VideoFeed videoFeed;
    private DJICodecManager codec;
    private SurfaceTexture surfaceTexture;
    private int surfaceWidth;
    private int surfaceHeight;
    private long lastGimbalAt;
    private volatile long lastZoomAt;
    private volatile float lastRequestedZoom = Float.NaN;
    private volatile float lastAppliedZoom = CameraDirector.MIN_DIGITAL_ZOOM;
    private volatile boolean digitalZoomSupported;
    private volatile boolean zoomCommandPending;
    private volatile boolean zoomFailureReported;
    private volatile long lastVideoPacketAt;
    private volatile int videoRestartAttempts;
    private volatile AircraftAction aircraftAction = AircraftAction.NONE;
    private volatile long aircraftActionStartedAt;
    private volatile boolean landingConfirmationSent;
    private volatile ReturnHomeStatus returnHomeStatus = ReturnHomeStatus.missing(
        "Před vzletem ulož a ověř návratový bod.");
    private volatile boolean lowBatteryRthTriggered;
    private volatile boolean smartRthConfirmationSent;
    private volatile boolean flightObserved;

    private final Runnable connectionRetry = new Runnable() {
        @Override public void run() {
            synchronized (DjiConnection.this) {
                if (closed || !registered || productConnected()) return;
                startProductConnection(false);
            }
        }
    };

    private final Runnable videoWatchdog = new Runnable() {
        @Override public void run() {
            synchronized (DjiConnection.this) {
                if (closed || aircraft == null || !aircraft.isConnected()
                        || surfaceTexture == null || codec == null) return;
                long idle = SystemClock.elapsedRealtime() - lastVideoPacketAt;
                if (idle >= VIDEO_STALE_MILLIS) {
                    if (videoRestartAttempts >= MAX_VIDEO_RESTARTS) {
                        postStatus("RC-N1 i kamera jsou připojené, ale nepřichází živý obraz. Zkontroluj přenos v DJI Fly a připoj USB znovu.");
                        return;
                    }
                    videoRestartAttempts++;
                    restartVideoPipeline();
                    return;
                }
                main.postDelayed(this, VIDEO_WATCHDOG_MILLIS);
            }
        }
    };

    private final VideoFeeder.VideoDataListener videoDataListener = (bytes, size) -> {
        if (bytes == null || size <= 0) return;
        lastVideoPacketAt = SystemClock.elapsedRealtime();
        videoRestartAttempts = 0;
        DJICodecManager current = codec;
        if (current != null) {
            try {
                current.sendDataToDecoder(bytes, size);
            } catch (RuntimeException error) {
                postStatus("Chyba dekódování živého obrazu: " + safeError(error));
            }
        }
        if (!videoActive) {
            videoActive = true;
            postVideo(true);
        }
    };

    private final VideoFeeder.VideoActiveStatusListener videoStatusListener = active -> {
        videoActive = active;
        postVideo(active);
    };

    DjiConnection(Activity activity) { this.activity = activity; }

    @Override public void setListener(Listener listener) {
        this.listener = listener;
        postTelemetry();
        postCamera(recording);
        postCameraStorage();
        postCameraAutomation();
        postAction();
        postReturnHomeStatus();
    }

    @Override public void connect() {
        if (closed) return;
        if (!hasValidKey()) {
            postStatus("V APK chybí platný DJI App Key pro balíček cz.dachman.drone.director.");
            return;
        }
        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) {
            activity.requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
            postStatus("Povol oprávnění potřebná pro spojení s DJI Mini 2.");
            return;
        }
        if (registered) {
            startProductConnection(true);
            return;
        }
        if (registering) {
            postStatus("Registrace DJI právě probíhá…");
            return;
        }
        registering = true;
        postStatus("Registruji aplikaci u DJI…");
        DJISDKManager.getInstance().registerApp(activity.getApplicationContext(), sdkCallback);
    }

    private final DJISDKManager.SDKManagerCallback sdkCallback = new DJISDKManager.SDKManagerCallback() {
        @Override public void onRegister(DJIError error) {
            registering = false;
            registered = error == DJISDKError.REGISTRATION_SUCCESS;
            postTelemetry();
            if (registered) {
                startProductConnection(true);
            } else {
                postStatus("Registrace DJI selhala: " + errorText(error));
            }
        }

        @Override public void onProductDisconnect() {
            releaseProduct("Mini 2 bylo odpojeno; čekám na obnovení spojení…");
            startProductConnection(true);
        }
        @Override public void onProductConnect(BaseProduct product) { bindProduct(product); }
        @Override public void onProductChanged(BaseProduct product) { bindProduct(product); }

        @Override public void onComponentChange(BaseProduct.ComponentKey key,
                BaseComponent oldComponent, BaseComponent newComponent) {
            BaseProduct current = DJISDKManager.getInstance().getProduct();
            if (current != null && current.isConnected()) bindProduct(current);
        }

        @Override public void onInitProcess(DJISDKInitEvent event, int progress) {
            if (progress == 100) postStatus("DJI služby inicializovány; čekám na registraci…");
        }

        @Override public void onDatabaseDownloadProgress(long current, long total) {}
    };

    @Override public synchronized void onUsbAccessoryAttached() {
        if (closed) return;
        djiUsbVisible = true;
        connectionAttempts = 0;
        main.removeCallbacks(connectionRetry);
        activity.sendBroadcast(new Intent(DJISDKManager.USB_ACCESSORY_ATTACHED));
        postStatus("Android rozpoznal USB ovladač DJI; navazuji spojení…");
        main.postDelayed(() -> {
            if (registered) startProductConnection(true);
            else connect();
        }, 250L);
    }

    private synchronized void startProductConnection(boolean resetAttempts) {
        if (closed || !registered) return;
        BaseProduct current = DJISDKManager.getInstance().getProduct();
        if (current != null && current.isConnected()) {
            bindProduct(current);
            return;
        }
        if (resetAttempts) {
            connectionAttempts = 0;
            main.removeCallbacks(connectionRetry);
        }
        if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS) {
            postStatus("RC-N1 nebyl nalezen. Znovu připoj datový kabel a jako USB aplikaci zvol Dachman AI.");
            return;
        }
        connectionAttempts++;
        boolean usbWasVisible = djiUsbVisible;
        djiUsbVisible = hasDjiUsbAccessory();
        boolean started;
        try {
            started = DJISDKManager.getInstance().startConnectionToProduct();
        } catch (RuntimeException error) {
            started = false;
            postStatus("DJI spojení nelze spustit: " + safeError(error));
        }
        if (resetAttempts || usbWasVisible != djiUsbVisible || connectionAttempts == 1) {
            if (djiUsbVisible) {
                postStatus("USB RC-N1 je dostupné; čekám na spojení s DJI Mini 2…");
            } else if (started) {
                postStatus("DJI SDK je připravené; čekám na USB RC-N1 a Mini 2…");
            } else {
                postStatus("Android zatím nepředal USB RC-N1. Připoj kabel znovu a zvol Dachman AI.");
            }
        }
        main.removeCallbacks(connectionRetry);
        main.postDelayed(connectionRetry, CONNECTION_RETRY_MILLIS);
    }

    private boolean productConnected() {
        BaseProduct current = DJISDKManager.getInstance().getProduct();
        return current != null && current.isConnected();
    }

    private boolean hasDjiUsbAccessory() {
        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) return false;
        UsbAccessory[] accessories = manager.getAccessoryList();
        if (accessories == null) return false;
        for (UsbAccessory accessory : accessories) {
            if (accessory != null && "DJI".equalsIgnoreCase(accessory.getManufacturer())) return true;
        }
        return false;
    }

    private synchronized void bindProduct(BaseProduct product) {
        if (closed) return;
        main.removeCallbacks(connectionRetry);
        connectionAttempts = 0;
        djiUsbVisible = true;
        unbindCallbacks();
        if (!(product instanceof Aircraft) || !product.isConnected()) {
            aircraft = null;
            compatibleModel = false;
            postStatus("Připojené zařízení není podporovaný DJI Mini 2.");
            postTelemetry();
            return;
        }
        aircraft = (Aircraft) product;
        Model model = product.getModel();
        compatibleModel = model == Model.DJI_MINI_2;
        flightController = aircraft.getFlightController();
        remoteController = aircraft.getRemoteController();
        battery = aircraft.getBattery();
        camera = aircraft.getCamera();
        gimbal = aircraft.getGimbal();
        airLink = aircraft.getAirLink();
        digitalZoomSupported = camera != null && camera.isConnected()
            && (compatibleModel || supportsDigitalZoom(camera));
        cameraStorageStatus = camera == null || !camera.isConnected()
            ? CameraStorageStatus.disconnected() : CameraStorageStatus.checking();
        returnHomeStatus = ReturnHomeStatus.missing("Polož dron na místo návratu a ulož bod vzletu.");
        lowBatteryRthTriggered = false;
        smartRthConfirmationSent = false;
        flightObserved = false;
        lastRequestedZoom = Float.NaN;
        lastAppliedZoom = CameraDirector.MIN_DIGITAL_ZOOM;
        zoomCommandPending = false;
        zoomFailureReported = false;
        postCameraAutomation();
        postCameraStorage();

        if (flightController != null) flightController.setStateCallback(this::onFlightState);
        if (battery != null) battery.setStateCallback(state -> {
            batteryPercent = state.getChargeRemainingInPercent();
            postTelemetry();
            handleBatterySafety();
        });
        if (airLink != null) airLink.setUplinkSignalQualityCallback(percent -> {
            signalPercent = percent;
            postTelemetry();
        });
        if (remoteController != null) remoteController.setHardwareStateCallback(this::onHardwareState);
        if (camera != null) camera.setSystemStateCallback(state -> {
            recording = state.isRecording();
            postCamera(recording);
        });
        if (camera != null) camera.setStorageStateCallBack(this::onCameraStorageState);
        videoRestartAttempts = 0;
        startVideoFeed();
        postStatus(compatibleModel
            ? "DJI Mini 2 připojen • RC-N1 " + componentState(remoteController)
                + " • kamera " + componentState(camera)
                + " • AI zoom " + (digitalZoomSupported ? "OK" : "není dostupný")
                + " • čekám na živý obraz."
            : "Připojený model není DJI Mini 2; živé řízení je uzamčeno.");
        postTelemetry();
        postReturnHomeStatus();
    }

    private void onCameraStorageState(StorageState state) {
        if (state == null || state.getStorageLocation() != SettingsDefinitions.StorageLocation.SDCARD) return;
        cameraStorageStatus = CameraStorageStatus.evaluate(
            state.isInserted(), state.isInitializing(), state.isReadOnly(), state.isFormatted(),
            state.isFormatting(), state.isFull(), state.isVerified(), state.hasError(),
            state.getAvailableRecordingTimeInSeconds());
        postCameraStorage();
        if (recording && !cameraStorageStatus.ready) {
            postStatus("VAROVÁNÍ ZÁZNAMU: " + cameraStorageStatus.detail);
        }
    }

    private void onFlightState(FlightControllerState state) {
        lastFlightState = state;
        lastFlightStateAt = android.os.SystemClock.elapsedRealtime();
        if (state.isFlying()) flightObserved = true;
        updateReturnHomeDistance(state);
        handleSmartRthRequest(state);
        AircraftAction action = aircraftAction;
        if ((action == AircraftAction.LANDING || action == AircraftAction.RETURN_HOME)
                && state.isLandingConfirmationNeeded() && !landingConfirmationSent) {
            landingConfirmationSent = true;
            FlightController current = flightController;
            if (current != null) current.confirmLanding(error -> {
                if (error != null) {
                    landingConfirmationSent = false;
                    postStatus("Potvrzení dosednutí selhalo: " + errorText(error));
                } else {
                    postStatus("Dosednutí pod třicet centimetrů bylo potvrzeno.");
                }
            });
        }
        long elapsed = android.os.SystemClock.elapsedRealtime() - aircraftActionStartedAt;
        if (action == AircraftAction.TAKEOFF && elapsed > 2_500L && state.isFlying()
                && altitude(state) >= 1.0f) {
            finishAircraftAction("Vzlet dokončen; dron visí a čeká na výběr režimu.");
        } else if ((action == AircraftAction.LANDING || action == AircraftAction.RETURN_HOME)
                && elapsed > 2_500L && !state.areMotorsOn() && !state.isFlying()) {
            finishAircraftAction(action == AircraftAction.LANDING
                ? "Autonomní přistání dokončeno." : "Návrat domů a přistání dokončeny.");
        }
        if (flightObserved && !state.areMotorsOn() && !state.isFlying()) {
            flightObserved = false;
            lowBatteryRthTriggered = false;
            smartRthConfirmationSent = false;
            returnHomeStatus = ReturnHomeStatus.missing(
                "Let skončil. Před dalším vzletem znovu ulož návratový bod.");
            postReturnHomeStatus();
        }
        postTelemetry();
    }

    private void updateReturnHomeDistance(FlightControllerState state) {
        ReturnHomeStatus status = returnHomeStatus;
        LocationCoordinate3D location = state.getAircraftLocation();
        if (!status.ready || location == null) return;
        double distance = ReturnHomeStatus.distanceMeters(location.getLatitude(), location.getLongitude(),
            status.latitude, status.longitude);
        returnHomeStatus = status.withDistanceToHome(distance);
        postReturnHomeStatus();
    }

    private void handleSmartRthRequest(FlightControllerState state) {
        if (state.getGoHomeAssessment() == null
                || state.getGoHomeAssessment().getSmartRTHState() != SmartRTHState.COUNTING_DOWN
                || smartRthConfirmationSent) return;
        smartRthConfirmationSent = true;
        beginAutomaticReturnHome("DJI Smart RTH vyhodnotilo nedostatek energie.", true);
    }

    private void handleBatterySafety() {
        FlightControllerState state = lastFlightState;
        if (state == null || batteryPercent < 0 || batteryPercent > AUTOMATIC_RTH_BATTERY_PERCENT
                || !state.isFlying() || state.isGoingHome() || lowBatteryRthTriggered) return;
        lowBatteryRthTriggered = true;
        beginAutomaticReturnHome("Baterie klesla na bezpečnostní mez; spouštím návrat domů.", false);
    }

    private void beginAutomaticReturnHome(String reason, boolean smartRequestActive) {
        FlightController current = flightController;
        FlightControllerState state = lastFlightState;
        if (current == null || state == null || !state.isHomeLocationSet()) {
            postStatus("KRITICKÉ: Automatický RTH nelze spustit bez platného Home Pointu. Pilot musí převzít řízení.");
            return;
        }
        if (!returnHomeStatus.ready) {
            postStatus("KRITICKÉ: Návratový bod nebyl aplikací ověřen. Pilot musí převzít řízení.");
            return;
        }
        withoutVirtualStick(reason, (success, message) -> {
            if (!success) postStatus("KRITICKÉ: " + message);
        }, () -> {
            setAircraftAction(AircraftAction.RETURN_HOME);
            if (smartRequestActive) {
                current.confirmSmartReturnToHomeRequest(true, error -> {
                    if (error == null) postStatus(reason + " Smart RTH potvrzeno.");
                    else startGoHomeFallback(current, reason);
                });
            } else {
                startGoHomeFallback(current, reason);
            }
        });
    }

    private void startGoHomeFallback(FlightController current, String reason) {
        current.startGoHome(error -> {
            if (error == null) postStatus(reason + " RTH převzal letový kontrolér DJI.");
            else {
                clearAircraftAction();
                postStatus("KRITICKÉ: Návrat domů se nepodařilo spustit: " + errorText(error));
            }
        });
    }

    private synchronized void onHardwareState(HardwareState state) {
        if (state == null) return;
        boolean gimbalDialMoved = Math.abs(state.getLeftDial()) > GIMBAL_DIAL_DEAD_ZONE;
        boolean deflected = stickMoved(state.getLeftStick()) || stickMoved(state.getRightStick())
            || gimbalDialMoved;
        manualDeflection = deflected;
        if (!deflected || pilotOverridePending) return;
        String pilotControl = gimbalDialMoved ? "zásah kolečkem gimbalu" : "zásah kniplem";
        if (virtualStickEnabled || virtualStickEnabling) {
            pilotOverridePending = true;
            postPilotOverride("PILOT OVERRIDE – " + pilotControl + " vypnul AI řízení i kameru.");
            disableVirtualStick("Pilot převzal řízení.", (success, message) -> pilotOverridePending = false);
        } else if (aircraftAction != AircraftAction.NONE) {
            pilotOverridePending = true;
            cancelAircraftAction((success, message) -> {
                pilotOverridePending = false;
                postPilotOverride("PILOT OVERRIDE – " + message);
            });
        }
    }

    private static boolean stickMoved(Stick stick) {
        return stick != null && (Math.abs(stick.getHorizontalPosition()) > RC_DEAD_ZONE
            || Math.abs(stick.getVerticalPosition()) > RC_DEAD_ZONE);
    }

    @Override public synchronized void attachVideo(SurfaceTexture texture, int width, int height) {
        surfaceTexture = texture;
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        videoRestartAttempts = 0;
        main.removeCallbacks(videoWatchdog);
        destroyCodecOnly();
        try {
            codec = new DJICodecManager(activity, texture, surfaceWidth, surfaceHeight);
        } catch (RuntimeException error) {
            codec = null;
            postStatus("Video dekodér DJI nelze spustit: " + safeError(error));
            return;
        }
        startVideoFeed();
    }

    private synchronized void startVideoFeed() {
        if (surfaceTexture == null || codec == null || aircraft == null || !aircraft.isConnected()) return;
        VideoFeeder.VideoFeed next;
        try {
            next = VideoFeeder.getInstance().getPrimaryVideoFeed();
        } catch (RuntimeException error) {
            postStatus("Video feed DJI není dostupný: " + safeError(error));
            return;
        }
        if (next == null) {
            postStatus("DJI zatím neposkytlo primární video feed; čekám na kameru…");
            main.removeCallbacks(videoWatchdog);
            main.postDelayed(videoWatchdog, VIDEO_WATCHDOG_MILLIS);
            return;
        }
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
        }
        videoFeed = next;
        videoFeed.addVideoDataListener(videoDataListener);
        videoFeed.addVideoActiveStatusListener(videoStatusListener);
        videoActive = false;
        lastVideoPacketAt = SystemClock.elapsedRealtime();
        postVideo(false);
        main.removeCallbacks(videoWatchdog);
        main.postDelayed(videoWatchdog, VIDEO_WATCHDOG_MILLIS);
    }

    private synchronized void restartVideoPipeline() {
        if (closed || surfaceTexture == null || aircraft == null || !aircraft.isConnected()) return;
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
            videoFeed = null;
        }
        videoActive = false;
        postVideo(false);
        destroyCodecOnly();
        try {
            codec = new DJICodecManager(activity, surfaceTexture, surfaceWidth, surfaceHeight);
            postStatus("Obnovuji živý obraz z kamery DJI…");
            startVideoFeed();
        } catch (RuntimeException error) {
            codec = null;
            postStatus("Obnovení video dekodéru selhalo: " + safeError(error));
        }
    }

    @Override public synchronized void detachVideo() {
        main.removeCallbacks(videoWatchdog);
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
            videoFeed = null;
        }
        videoActive = false;
        videoRestartAttempts = 0;
        destroyCodecOnly();
        surfaceTexture = null;
        postVideo(false);
    }

    private void destroyCodecOnly() {
        DJICodecManager current = codec;
        codec = null;
        if (current != null) current.destroyCodec();
    }

    @Override public boolean supportsLiveControl() {
        Aircraft currentAircraft = aircraft;
        FlightController currentFlightController = flightController;
        RemoteController currentRemote = remoteController;
        return registered && compatibleModel && currentAircraft != null && currentAircraft.isConnected()
            && currentFlightController != null && currentFlightController.isConnected()
            && currentRemote != null && currentRemote.isConnected();
    }

    @Override public synchronized void enableVirtualStick(Completion completion) {
        FlightController current = flightController;
        if (!supportsLiveControl() || current == null) {
            postCompletion(completion, false, "Mini 2 a RC-N1 nejsou připravené pro řízení.");
            return;
        }
        if (aircraftAction != AircraftAction.NONE) {
            postCompletion(completion, false, "Nejprve dokonči nebo zruš " + aircraftAction.label + ".");
            return;
        }
        if (manualDeflection) {
            postCompletion(completion, false, "Nejprve pusť oba kniply do středu.");
            return;
        }
        current.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        current.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
        current.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        current.setVerticalControlMode(VerticalControlMode.VELOCITY);
        final long generation = ++virtualStickRevision;
        virtualStickEnabling = true;
        current.setVirtualStickModeEnabled(true, error -> {
            main.post(() -> {
                if (generation != virtualStickRevision || closed || current != flightController) {
                    postCompletion(completion, false, "Spuštění Virtual Stick bylo zrušeno.");
                    return;
                }
                if (error != null) {
                    virtualStickEnabling = false;
                    current.setVirtualStickModeEnabled(false, ignored -> {});
                    postCompletion(completion, false, "Virtual Stick nelze zapnout: " + errorText(error));
                    return;
                }
                verifyVirtualStick(current, generation, 0, completion);
            });
        });
    }

    private synchronized void verifyVirtualStick(FlightController current, long generation,
            int attempt, Completion completion) {
        if (generation != virtualStickRevision || closed || current != flightController) {
            postCompletion(completion, false, "Spuštění Virtual Stick bylo zrušeno.");
            return;
        }
        if (manualDeflection) {
            disableVirtualStick("Pilot zasáhl během aktivace.", (success, message) -> {});
            postPilotOverride("PILOT OVERRIDE – zásah během aktivace AI.");
            postCompletion(completion, false, "Pilot zasáhl během aktivace.");
            return;
        }
        if (current.isVirtualStickControlModeAvailable()) {
            current.setVirtualStickAdvancedModeEnabled(true);
            virtualStickEnabled = true;
            virtualStickEnabling = false;
            postCompletion(completion, true, "DJI potvrdilo dostupné řízení Virtual Stick.");
        } else if (attempt < 6) {
            main.postDelayed(() -> verifyVirtualStick(current, generation, attempt + 1, completion), 250L);
        } else {
            disableVirtualStick("Virtual Stick není dostupný.", (success, message) -> {});
            postCompletion(completion, false,
                "DJI nepovolilo pohyb Virtual Stick. Ověř letový režim ovladače, polohu a stav dronu.");
        }
    }

    @Override public synchronized void sendCommand(FlightCommand command, Completion completion) {
        FlightController current = flightController;
        if (!virtualStickEnabled || current == null || command == null || manualDeflection
                || !current.isVirtualStickControlModeAvailable()) {
            completion.onComplete(false, "Virtual Stick není dostupný nebo zasáhl pilot.");
            return;
        }
        final VirtualStickPacket packet;
        try { packet = VirtualStickPacket.from(command); }
        catch (IllegalArgumentException error) {
            completion.onComplete(false, error.getMessage());
            return;
        }
        current.sendVirtualStickFlightControlData(
            new FlightControlData(packet.pitch, packet.roll, packet.yaw, packet.vertical), error -> {
                completion.onComplete(error == null, error == null ? "DJI povel přijalo." : errorText(error));
                if (error == null) return;
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastVirtualStickErrorAt < 2_000L) return;
                lastVirtualStickErrorAt = now;
                postStatus("DJI odmítlo pohybový povel Virtual Stick: " + errorText(error));
            });
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastGimbalAt >= GIMBAL_INTERVAL_MILLIS) {
            lastGimbalAt = now;
            rotateGimbal(command.gimbalPitch);
        }
        applyDigitalZoom(command.digitalZoomFactor, now);
    }

    private void rotateGimbal(float pitchSpeed) {
        Gimbal current = gimbal;
        if (current == null) return;
        Rotation rotation = new Rotation.Builder().mode(RotationMode.SPEED)
            .pitch(pitchSpeed).roll(Rotation.NO_ROTATION).yaw(Rotation.NO_ROTATION).build();
        current.rotate(rotation, null);
    }

    private void applyDigitalZoom(float requestedFactor, long now) {
        Camera current = camera;
        if (!Float.isFinite(requestedFactor) || !digitalZoomSupported || current == null
                || !current.isConnected() || zoomCommandPending
                || now - lastZoomAt < ZOOM_INTERVAL_MILLIS) return;
        float factor = Math.max(CameraDirector.MIN_DIGITAL_ZOOM,
            Math.min(CameraDirector.MAX_DIGITAL_ZOOM, requestedFactor));
        if (Float.isFinite(lastRequestedZoom)
                && Math.abs(lastRequestedZoom - factor) < ZOOM_EPSILON) return;
        lastZoomAt = now;
        lastRequestedZoom = factor;
        zoomCommandPending = true;
        current.setDigitalZoomFactor(factor, error -> {
            zoomCommandPending = false;
            if (error == null) {
                zoomFailureReported = false;
                lastAppliedZoom = factor;
                postCameraAutomation();
                return;
            }
            lastRequestedZoom = Float.NaN;
            if (!zoomFailureReported) {
                zoomFailureReported = true;
                postStatus("Digitální zoom Mini 2 tento režim nepřijal: " + errorText(error)
                    + ". Automatický gimbal zůstává aktivní.");
            }
        });
    }

    private static boolean supportsDigitalZoom(Camera value) {
        if (value == null || !value.isConnected()) return false;
        try {
            return value.isDigitalZoomSupported();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override public synchronized void disableVirtualStick(String reason, Completion completion) {
        virtualStickRevision++;
        virtualStickEnabling = false;
        FlightController current = flightController;
        virtualStickEnabled = false;
        if (current == null) {
            postCompletion(completion, true, reason);
            return;
        }
        current.sendVirtualStickFlightControlData(new FlightControlData(0f, 0f, 0f, 0f), null);
        rotateGimbal(0f);
        current.setVirtualStickAdvancedModeEnabled(false);
        current.setVirtualStickModeEnabled(false, error -> postCompletion(completion, error == null,
            error == null ? reason : reason + " Vypnutí Virtual Stick: " + errorText(error)));
    }

    @Override public void startTakeoff(Completion completion) {
        String blocked = validateTakeoff();
        if (blocked != null) {
            postCompletion(completion, false, blocked);
            return;
        }
        withoutVirtualStick("Příprava autonomního vzletu", completion, () -> {
            setAircraftAction(AircraftAction.TAKEOFF);
            flightController.startTakeoff(error -> {
                if (error != null) {
                    clearAircraftAction();
                    postCompletion(completion, false, "Vzlet selhal: " + errorText(error));
                } else {
                    postCompletion(completion, true, "Vzlet zahájen; dron automaticky vystoupá do visu.");
                }
            });
        });
    }

    @Override public void prepareReturnHome(int heightMeters, Completion completion) {
        FlightController current = flightController;
        FlightControllerState state = lastFlightState;
        if (!supportsLiveControl() || current == null || state == null) {
            postCompletion(completion, false, "Mini 2, RC-N1 a živá telemetrie nejsou připravené.");
            return;
        }
        if (state.areMotorsOn() || state.isFlying()) {
            postCompletion(completion, false, "Návratový bod lze uložit pouze před spuštěním motorů.");
            return;
        }
        LocationCoordinate3D aircraftLocation = state.getAircraftLocation();
        String homeBlocked = HomePointPolicy.aircraftBlock(telemetry());
        if (aircraftLocation == null || homeBlocked != null) {
            postCompletion(completion, false, homeBlocked == null ? "Poloha dronu není dostupná." : homeBlocked);
            return;
        }
        if (heightMeters < 20 || heightMeters > 500) {
            postCompletion(completion, false, "RTH výška musí být mezi dvaceti a pěti sty metry.");
            return;
        }
        returnHomeStatus = ReturnHomeStatus.configuring();
        postReturnHomeStatus();
        final double requestedLatitude = aircraftLocation.getLatitude();
        final double requestedLongitude = aircraftLocation.getLongitude();
        current.setHomeLocationUsingAircraftCurrentLocation(error -> {
            if (error != null) {
                failReturnHomePreparation(completion, "Home Point nelze uložit: " + errorText(error));
                return;
            }
            current.getHomeLocation(new CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D>() {
                @Override public void onSuccess(LocationCoordinate2D home) {
                    if (home == null) {
                        failReturnHomePreparation(completion, "Uložený Home Point nelze zpětně načíst.");
                        return;
                    }
                    double verificationError = ReturnHomeStatus.distanceMeters(requestedLatitude,
                        requestedLongitude, home.getLatitude(), home.getLongitude());
                    if (Double.isNaN(verificationError)
                            || verificationError > ReturnHomeStatus.MAXIMUM_HOME_VERIFICATION_ERROR_METERS) {
                        failReturnHomePreparation(completion,
                            "Home Point se liší od polohy dronu o více než deset metrů.");
                        return;
                    }
                    configureReturnHomeHeight(current, home, verificationError, heightMeters, completion);
                }

                @Override public void onFailure(DJIError error) {
                    failReturnHomePreparation(completion,
                        "Uložený Home Point nelze zpětně načíst: " + errorText(error));
                }
            });
        });
    }

    @Override public void prepareReturnHomeFromDevice(double latitude, double longitude,
            float accuracyMeters, int heightMeters, Completion completion) {
        FlightController current = flightController;
        FlightControllerState state = lastFlightState;
        if (!supportsLiveControl() || current == null || state == null) {
            postCompletion(completion, false, "Mini 2, RC-N1 a živá telemetrie nejsou připravené.");
            return;
        }
        if (state.areMotorsOn() || state.isFlying()) {
            postCompletion(completion, false, "Návratový bod lze uložit pouze před spuštěním motorů.");
            return;
        }
        String blocked = HomePointPolicy.phoneBlock(telemetry(), latitude, longitude, accuracyMeters, 0L);
        if (blocked != null) {
            postCompletion(completion, false, blocked);
            return;
        }
        if (heightMeters < 20 || heightMeters > 500) {
            postCompletion(completion, false, "RTH výška musí být mezi dvaceti a pěti sty metry.");
            return;
        }
        returnHomeStatus = ReturnHomeStatus.configuring();
        postReturnHomeStatus();
        LocationCoordinate2D requested = new LocationCoordinate2D(latitude, longitude);
        current.setHomeLocation(requested, error -> {
            if (error != null) {
                failReturnHomePreparation(completion,
                    "DJI nepřijalo Home Point z telefonu: " + errorText(error)
                        + ". Bod NENÍ uložen. Ověř GNSS dronu venku; oprávnění telefonu toto omezení neřeší.");
                return;
            }
            current.getHomeLocation(new CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D>() {
                @Override public void onSuccess(LocationCoordinate2D home) {
                    if (home == null) {
                        failReturnHomePreparation(completion, "Home Point z telefonu nelze zpětně načíst.");
                        return;
                    }
                    double verificationError = ReturnHomeStatus.distanceMeters(latitude, longitude,
                        home.getLatitude(), home.getLongitude());
                    if (Double.isNaN(verificationError)
                            || verificationError > ReturnHomeStatus.MAXIMUM_HOME_VERIFICATION_ERROR_METERS) {
                        failReturnHomePreparation(completion,
                            "DJI uložilo Home Point mimo povolenou odchylku deseti metrů.");
                        return;
                    }
                    configureReturnHomeHeight(current, home, verificationError, heightMeters, completion);
                }

                @Override public void onFailure(DJIError error) {
                    failReturnHomePreparation(completion,
                        "Home Point z telefonu nelze zpětně načíst: " + errorText(error));
                }
            });
        });
    }

    private void configureReturnHomeHeight(FlightController current, LocationCoordinate2D home,
            double verificationError, int heightMeters, Completion completion) {
        current.setGoHomeHeightInMeters(heightMeters, error -> {
            if (error != null) {
                failReturnHomePreparation(completion, "RTH výšku nelze nastavit: " + errorText(error));
                return;
            }
            current.getGoHomeHeightInMeters(new CommonCallbacks.CompletionCallbackWith<Integer>() {
                @Override public void onSuccess(Integer storedHeight) {
                    if (storedHeight == null || storedHeight != heightMeters) {
                        failReturnHomePreparation(completion, "Letový kontrolér nepotvrdil zvolenou RTH výšku.");
                        return;
                    }
                    configureSmartRth(current, home, verificationError, storedHeight, completion);
                }

                @Override public void onFailure(DJIError error) {
                    failReturnHomePreparation(completion,
                        "RTH výšku nelze zpětně načíst: " + errorText(error));
                }
            });
        });
    }

    private void configureSmartRth(FlightController current, LocationCoordinate2D home,
            double verificationError, int heightMeters, Completion completion) {
        current.setSmartReturnToHomeEnabled(true, error -> {
            if (error != null) {
                failReturnHomePreparation(completion, "Smart RTH nelze zapnout: " + errorText(error));
                return;
            }
            current.getSmartReturnToHomeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
                @Override public void onSuccess(Boolean enabled) {
                    if (enabled == null || !enabled) {
                        failReturnHomePreparation(completion, "Letový kontrolér nepotvrdil Smart RTH.");
                        return;
                    }
                    configureConnectionFailsafe(current, home, verificationError, heightMeters, completion);
                }

                @Override public void onFailure(DJIError error) {
                    failReturnHomePreparation(completion,
                        "Smart RTH nelze zpětně ověřit: " + errorText(error));
                }
            });
        });
    }

    private void configureConnectionFailsafe(FlightController current, LocationCoordinate2D home,
            double verificationError, int heightMeters, Completion completion) {
        current.setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.GO_HOME, error -> {
            if (error != null) {
                failReturnHomePreparation(completion, "Failsafe GO_HOME nelze nastavit: " + errorText(error));
                return;
            }
            current.getConnectionFailSafeBehavior(
                new CommonCallbacks.CompletionCallbackWith<ConnectionFailSafeBehavior>() {
                    @Override public void onSuccess(ConnectionFailSafeBehavior behavior) {
                        boolean goHome = behavior == ConnectionFailSafeBehavior.GO_HOME;
                        returnHomeStatus = ReturnHomeStatus.verified(home.getLatitude(), home.getLongitude(),
                            System.currentTimeMillis(), verificationError, heightMeters, true, goHome);
                        postReturnHomeStatus();
                        if (returnHomeStatus.ready) {
                            postCompletion(completion, true,
                                "Návratový bod, RTH výška, Smart RTH a failsafe GO_HOME jsou ověřené.");
                        } else {
                            failReturnHomePreparation(completion, "Failsafe GO_HOME nebyl zpětně potvrzen.");
                        }
                    }

                    @Override public void onFailure(DJIError error) {
                        failReturnHomePreparation(completion,
                            "Failsafe GO_HOME nelze zpětně ověřit: " + errorText(error));
                    }
                });
        });
    }

    private void failReturnHomePreparation(Completion completion, String message) {
        returnHomeStatus = ReturnHomeStatus.missing(message);
        postReturnHomeStatus();
        postCompletion(completion, false, message);
    }

    @Override public void startLanding(Completion completion) {
        String blocked = validateAirborneAction(false);
        if (blocked != null) {
            postCompletion(completion, false, blocked);
            return;
        }
        withoutVirtualStick("Příprava autonomního přistání", completion, () -> {
            setAircraftAction(AircraftAction.LANDING);
            flightController.startLanding(error -> {
                if (error != null) {
                    clearAircraftAction();
                    postCompletion(completion, false, "Přistání selhalo: " + errorText(error));
                } else {
                    postCompletion(completion, true, "Autonomní přistání zahájeno; sleduj prostor pod dronem.");
                }
            });
        });
    }

    @Override public void startReturnHome(Completion completion) {
        String blocked = validateAirborneAction(true);
        if (blocked != null) {
            postCompletion(completion, false, blocked);
            return;
        }
        withoutVirtualStick("Příprava návratu domů", completion, () -> {
            setAircraftAction(AircraftAction.RETURN_HOME);
            flightController.startGoHome(error -> {
                if (error != null) {
                    clearAircraftAction();
                    postCompletion(completion, false, "Návrat domů selhal: " + errorText(error));
                } else {
                    postCompletion(completion, true, "Návrat domů zahájen; pilot musí sledovat celou trasu.");
                }
            });
        });
    }

    private void withoutVirtualStick(String reason, Completion completion, Runnable action) {
        if (!virtualStickEnabled) {
            action.run();
            return;
        }
        disableVirtualStick(reason, (success, message) -> {
            if (success) action.run();
            else postCompletion(completion, false, message);
        });
    }

    @Override public void cancelAircraftAction(Completion completion) {
        FlightController current = flightController;
        AircraftAction action = aircraftAction;
        if (current == null || action == AircraftAction.NONE) {
            postCompletion(completion, false, "Neprobíhá žádná automatická akce.");
            return;
        }
        dji.common.util.CommonCallbacks.CompletionCallback<DJIError> callback = error -> {
            if (error == null) {
                clearAircraftAction();
                postCompletion(completion, true, "Automatická akce zrušena; dron přechází do visu.");
            } else {
                postCompletion(completion, false, "Akci nelze zrušit: " + errorText(error));
            }
        };
        if (action == AircraftAction.TAKEOFF) current.cancelTakeoff(callback);
        else if (action == AircraftAction.LANDING) current.cancelLanding(callback);
        else current.cancelGoHome(callback);
    }

    private String validateTakeoff() {
        String common = validateCommon();
        if (common != null) return common;
        FlightControllerState state = lastFlightState;
        if (state == null) return "Čekám na živou telemetrii letového kontroléru.";
        if (state.areMotorsOn() || state.isFlying()) return "Dron už má spuštěné motory nebo letí.";
        if (batteryPercent < SafetySupervisor.MINIMUM_BATTERY_PERCENT) return "Pro vzlet je potřeba alespoň dvacet pět procent baterie.";
        if (signalPercent < SafetySupervisor.MINIMUM_SIGNAL_PERCENT) return "Rádiové spojení je pro vzlet příliš slabé.";
        if (!state.isHomeLocationSet() && !returnHomeStatus.ready) return "Domovský bod zatím není uložen.";
        if (!returnHomeStatus.ready) return "Vzlet je zablokovaný: v aplikaci ulož a ověř návratový bod.";
        String positionBlocked = HomePointPolicy.aircraftBlock(telemetry());
        if (positionBlocked != null) return positionBlocked;
        if (state.isFailsafeEnabled()) return "DJI failsafe je aktivní.";
        if (state.getFlightWindWarning() != null && "LEVEL_2".equals(state.getFlightWindWarning().name())) return "Silný vítr blokuje autonomní vzlet.";
        return null;
    }

    private String validateAirborneAction(boolean returnHome) {
        String common = validateCommon();
        if (common != null) return common;
        FlightControllerState state = lastFlightState;
        if (state == null) return "Čekám na živou telemetrii letového kontroléru.";
        if (!state.areMotorsOn() || !state.isFlying()) return "Dron právě neletí.";
        if (returnHome && !state.isHomeLocationSet() && !returnHomeStatus.ready) {
            return "Návrat domů nelze spustit bez uloženého domovského bodu.";
        }
        if (returnHome && !returnHomeStatus.ready) return "Návrat domů je zablokovaný: Home Point nebyl aplikací ověřen.";
        return null;
    }

    private String validateCommon() {
        if (!supportsLiveControl() || flightController == null) return "Mini 2 a RC-N1 nejsou připravené.";
        if (manualDeflection) return "Pusť oba kniply do středu.";
        if (aircraftAction != AircraftAction.NONE) return "Už probíhá " + aircraftAction.label + ".";
        return null;
    }

    private void setAircraftAction(AircraftAction action) {
        aircraftAction = action;
        aircraftActionStartedAt = android.os.SystemClock.elapsedRealtime();
        landingConfirmationSent = false;
        postAction();
    }

    private void clearAircraftAction() {
        aircraftAction = AircraftAction.NONE;
        aircraftActionStartedAt = 0L;
        landingConfirmationSent = false;
        postAction();
    }

    private void finishAircraftAction(String message) {
        clearAircraftAction();
        postStatus(message);
    }

    @Override public void toggleRecording(Completion completion) {
        Camera current = camera;
        if (current == null || !current.isConnected()) {
            postCompletion(completion, false, "Kamera není připojena.");
            return;
        }
        if (!recording && !cameraStorageStatus.ready) {
            postCompletion(completion, false, cameraStorageStatus.detail);
            return;
        }
        synchronized (this) {
            if (recordingCommandPending) {
                postCompletion(completion, false, "Kamera právě zpracovává předchozí příkaz nahrávání.");
                return;
            }
            recordingCommandPending = true;
        }
        if (recording) {
            current.stopRecordVideo(error -> {
                synchronized (this) { recordingCommandPending = false; }
                if (error == null) { recording = false; postCamera(false); }
                postCompletion(completion, error == null,
                    error == null ? "Nahrávání zastaveno." : "Nahrávání nelze zastavit: " + errorText(error));
            });
            return;
        }
        setNormalVideoMode(current, error -> {
            if (error != null) {
                synchronized (this) { recordingCommandPending = false; }
                postCompletion(completion, false, "Režim videa nelze nastavit: " + errorText(error));
                return;
            }
            current.startRecordVideo(startError -> {
                synchronized (this) { recordingCommandPending = false; }
                if (startError == null) { recording = true; postCamera(true); }
                postCompletion(completion, startError == null,
                    startError == null ? "Nahrávání spuštěno." : "Nahrávání nelze spustit: " + errorText(startError));
            });
        });
    }

    /** Mini 2 firmware uses Flat Camera Mode when the SDK reports it as available. */
    private void setNormalVideoMode(Camera current,
            dji.common.util.CommonCallbacks.CompletionCallback<DJIError> completion) {
        boolean flatModeSupported;
        try {
            flatModeSupported = current.isFlatCameraModeSupported();
        } catch (RuntimeException ignored) {
            flatModeSupported = false;
        }
        if (flatModeSupported) {
            current.setFlatMode(SettingsDefinitions.FlatCameraMode.VIDEO_NORMAL, completion);
        } else {
            current.setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, completion);
        }
    }

    @Override public void takePhoto(Completion completion) {
        Camera current = camera;
        if (current == null || !current.isConnected()) {
            postCompletion(completion, false, "Kamera není připojena.");
            return;
        }
        if (recording) {
            postCompletion(completion, false, "Nejprve zastav nahrávání videa.");
            return;
        }
        current.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, error -> {
            if (error != null) {
                postCompletion(completion, false, "Režim fotografie nelze nastavit: " + errorText(error));
                return;
            }
            current.startShootPhoto(photoError -> postCompletion(completion, photoError == null,
                photoError == null ? "Fotografie pořízena." : "Fotografii nelze pořídit: " + errorText(photoError)));
        });
    }

    private synchronized void releaseProduct(String message) {
        virtualStickEnabled = false;
        main.removeCallbacks(videoWatchdog);
        unbindCallbacks();
        aircraft = null;
        flightController = null;
        remoteController = null;
        battery = null;
        camera = null;
        gimbal = null;
        airLink = null;
        compatibleModel = false;
        batteryPercent = -1;
        signalPercent = -1;
        lastFlightState = null;
        recording = false;
        recordingCommandPending = false;
        cameraStorageStatus = CameraStorageStatus.disconnected();
        returnHomeStatus = ReturnHomeStatus.missing("Dron není připojen.");
        lowBatteryRthTriggered = false;
        smartRthConfirmationSent = false;
        flightObserved = false;
        videoActive = false;
        digitalZoomSupported = false;
        zoomCommandPending = false;
        zoomFailureReported = false;
        lastRequestedZoom = Float.NaN;
        lastAppliedZoom = CameraDirector.MIN_DIGITAL_ZOOM;
        lastZoomAt = 0L;
        postCameraAutomation();
        postCameraStorage();
        postReturnHomeStatus();
        clearAircraftAction();
        postStatus(message);
        postVideo(false);
        postCamera(false);
        postTelemetry();
    }

    private void unbindCallbacks() {
        if (flightController != null) flightController.setStateCallback(null);
        if (remoteController != null) remoteController.setHardwareStateCallback(null);
        if (battery != null) battery.setStateCallback(null);
        if (camera != null) camera.setSystemStateCallback(null);
        if (camera != null) camera.setStorageStateCallBack(null);
        if (airLink != null) airLink.setUplinkSignalQualityCallback(null);
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
            videoFeed = null;
        }
    }

    private TelemetrySnapshot telemetry() {
        Aircraft currentAircraft = aircraft;
        FlightControllerState state = lastFlightState;
        boolean connected = currentAircraft != null && currentAircraft.isConnected() && compatibleModel;
        String product = "Nepřipojeno";
        if (currentAircraft != null && currentAircraft.getModel() != null) product = currentAircraft.getModel().getDisplayName();
        if (state == null || android.os.SystemClock.elapsedRealtime() - lastFlightStateAt > 1_500L) {
            return new TelemetrySnapshot(registered, connected, product, "—", batteryPercent, 0,
                0f, 0f, 0f, signalPercent, false, false, false, false, "UNKNOWN");
        }
        float horizontal = (float)Math.hypot(state.getVelocityX(), state.getVelocityY());
        String wind = state.getFlightWindWarning() == null ? "UNKNOWN" : state.getFlightWindWarning().name();
        LocationCoordinate3D aircraftLocation = state.getAircraftLocation();
        LocationCoordinate2D homeLocation = state.getHomeLocation();
        double aircraftLatitude = aircraftLocation == null ? Double.NaN : aircraftLocation.getLatitude();
        double aircraftLongitude = aircraftLocation == null ? Double.NaN : aircraftLocation.getLongitude();
        String gpsLevel = state.getGPSSignalLevel() == null ? "UNKNOWN" : state.getGPSSignalLevel().name();
        // Coordinates can remain cached after GNSS loss; never treat them as a live position.
        if (!("LEVEL_3".equals(gpsLevel) || "LEVEL_4".equals(gpsLevel) || "LEVEL_5".equals(gpsLevel))) {
            aircraftLatitude = Double.NaN;
            aircraftLongitude = Double.NaN;
        }
        double homeLatitude = homeLocation == null ? Double.NaN : homeLocation.getLatitude();
        double homeLongitude = homeLocation == null ? Double.NaN : homeLocation.getLongitude();
        boolean homeLocationSet = state.isHomeLocationSet();
        if (!homeLocationSet && returnHomeStatus.ready) {
            homeLatitude = returnHomeStatus.latitude;
            homeLongitude = returnHomeStatus.longitude;
            homeLocationSet = true;
        }
        float heading = state.getAttitude() == null ? 0f : (float)state.getAttitude().yaw;
        return new TelemetrySnapshot(registered, connected, product, state.getFlightModeString(),
            batteryPercent, state.getSatelliteCount(), altitude(state), horizontal, state.getVelocityZ(),
            signalPercent, state.areMotorsOn(), state.isFlying(), state.isFailsafeEnabled(),
            state.isGoingHome(), wind, aircraftLatitude, aircraftLongitude, homeLatitude, homeLongitude,
            heading, homeLocationSet);
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
            && latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d
            && !(Math.abs(latitude) < 0.000001d && Math.abs(longitude) < 0.000001d);
    }

    private static float altitude(FlightControllerState state) {
        LocationCoordinate3D location = state.getAircraftLocation();
        return location == null ? 0f : location.getAltitude();
    }

    private boolean hasValidKey() {
        try {
            ApplicationInfo info = activity.getPackageManager().getApplicationInfo(
                activity.getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData == null ? "" : info.metaData.getString("com.dji.sdk.API_KEY", "");
            String normalized = key == null ? "" : key.trim().toLowerCase();
            return !normalized.isEmpty() && !normalized.contains("__")
                && !normalized.contains("$(") && !normalized.contains("placeholder");
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> missingPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT <= 32) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        return missing;
    }

    private void postStatus(String message) {
        main.post(() -> { if (!closed && listener != null) listener.onStatus(message); });
    }

    private void postTelemetry() {
        TelemetrySnapshot value = telemetry();
        main.post(() -> { if (!closed && listener != null) listener.onTelemetry(value); });
    }

    private void postVideo(boolean active) {
        main.post(() -> { if (!closed && listener != null) listener.onVideoState(active); });
    }

    private void postPilotOverride(String reason) {
        main.post(() -> { if (!closed && listener != null) listener.onPilotOverride(reason); });
    }

    private void postCamera(boolean isRecording) {
        main.post(() -> { if (!closed && listener != null) listener.onCameraState(isRecording); });
    }

    private void postCameraStorage() {
        CameraStorageStatus status = cameraStorageStatus;
        main.post(() -> { if (!closed && listener != null) listener.onCameraStorageState(status); });
    }

    private void postCameraAutomation() {
        boolean supported = digitalZoomSupported;
        float factor = lastAppliedZoom;
        main.post(() -> {
            if (!closed && listener != null) {
                listener.onCameraAutomationState(supported, factor);
            }
        });
    }

    private void postAction() {
        AircraftAction value = aircraftAction;
        main.post(() -> { if (!closed && listener != null) listener.onAircraftAction(value.label, value != AircraftAction.NONE); });
    }

    private void postReturnHomeStatus() {
        ReturnHomeStatus status = returnHomeStatus;
        main.post(() -> { if (!closed && listener != null) listener.onReturnHomeStatus(status); });
    }

    private void postCompletion(Completion completion, boolean success, String message) {
        if (completion != null) main.post(() -> completion.onComplete(success, message));
    }

    private static String errorText(DJIError error) {
        if (error == null) return "neznámá chyba";
        String description = error.getDescription();
        return description == null || description.trim().isEmpty() ? error.toString() : description;
    }

    private static String safeError(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String componentState(BaseComponent component) {
        return component != null && component.isConnected() ? "OK" : "čeká";
    }

    @Override public synchronized void close() {
        if (closed) return;
        main.removeCallbacks(connectionRetry);
        main.removeCallbacks(videoWatchdog);
        if (virtualStickEnabled && flightController != null) {
            flightController.sendVirtualStickFlightControlData(new FlightControlData(0f, 0f, 0f, 0f), null);
            flightController.setVirtualStickAdvancedModeEnabled(false);
            flightController.setVirtualStickModeEnabled(false, null);
        }
        virtualStickEnabled = false;
        detachVideo();
        unbindCallbacks();
        DJISDKManager.getInstance().stopConnectionToProduct();
        listener = null;
        closed = true;
    }
}
