package cz.dachman.drone.director;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import dji.common.Stick;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
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
    private static final long GIMBAL_INTERVAL_MILLIS = 180L;

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
    private boolean virtualStickEnabled;
    private boolean manualDeflection;
    private boolean pilotOverridePending;
    private boolean recording;
    private boolean videoActive;
    private boolean compatibleModel;
    private int batteryPercent = -1;
    private int signalPercent = -1;
    private FlightControllerState lastFlightState;
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
    private volatile AircraftAction aircraftAction = AircraftAction.NONE;
    private volatile long aircraftActionStartedAt;
    private volatile boolean landingConfirmationSent;

    private final VideoFeeder.VideoDataListener videoDataListener = (bytes, size) -> {
        DJICodecManager current = codec;
        if (current != null && bytes != null && size > 0) current.sendDataToDecoder(bytes, size);
        if (!videoActive && size > 0) {
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
        postAction();
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
            postStatus("DJI SDK je připravené; hledám ovladač a Mini 2…");
            DJISDKManager.getInstance().startConnectionToProduct();
            BaseProduct current = DJISDKManager.getInstance().getProduct();
            if (current != null && current.isConnected()) bindProduct(current);
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
                postStatus("DJI registrace je hotová; připojuji ovladač…");
                DJISDKManager.getInstance().startConnectionToProduct();
            } else {
                postStatus("Registrace DJI selhala: " + errorText(error));
            }
        }

        @Override public void onProductDisconnect() { releaseProduct("Mini 2 bylo odpojeno."); }
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

    private synchronized void bindProduct(BaseProduct product) {
        if (closed) return;
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

        if (flightController != null) flightController.setStateCallback(this::onFlightState);
        if (battery != null) battery.setStateCallback(state -> {
            batteryPercent = state.getChargeRemainingInPercent();
            postTelemetry();
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
        startVideoFeed();
        postStatus(compatibleModel
            ? "DJI Mini 2 je připojený. Zkontroluj prostor a vyber požadovanou akci."
            : "Připojený model není DJI Mini 2; živé řízení je uzamčeno.");
        postTelemetry();
    }

    private void onFlightState(FlightControllerState state) {
        lastFlightState = state;
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
        postTelemetry();
    }

    private void onHardwareState(HardwareState state) {
        boolean deflected = stickMoved(state.getLeftStick()) || stickMoved(state.getRightStick());
        manualDeflection = deflected;
        if (!deflected || pilotOverridePending) return;
        if (virtualStickEnabled) {
            pilotOverridePending = true;
            virtualStickEnabled = false;
            FlightController current = flightController;
            if (current == null) {
                pilotOverridePending = false;
                postPilotOverride("PILOT OVERRIDE – AI řízení bylo zastaveno.");
            } else {
                current.setVirtualStickModeEnabled(false, error -> {
                    pilotOverridePending = false;
                    postPilotOverride("PILOT OVERRIDE – pohyb kniplu vypnul AI řízení.");
                });
            }
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
        destroyCodecOnly();
        codec = new DJICodecManager(activity, texture, surfaceWidth, surfaceHeight);
        startVideoFeed();
    }

    private synchronized void startVideoFeed() {
        if (surfaceTexture == null || codec == null || aircraft == null) return;
        VideoFeeder.VideoFeed next = VideoFeeder.getInstance().getPrimaryVideoFeed();
        if (videoFeed == next) return;
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
        }
        videoFeed = next;
        videoFeed.addVideoDataListener(videoDataListener);
        videoFeed.addVideoActiveStatusListener(videoStatusListener);
    }

    @Override public synchronized void detachVideo() {
        if (videoFeed != null) {
            videoFeed.removeVideoDataListener(videoDataListener);
            videoFeed.removeVideoActiveStatusListener(videoStatusListener);
            videoFeed = null;
        }
        videoActive = false;
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

    @Override public void enableVirtualStick(Completion completion) {
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
        current.setVirtualStickAdvancedModeEnabled(true);
        current.setVirtualStickModeEnabled(true, error -> {
            boolean success = error == null;
            virtualStickEnabled = success;
            postCompletion(completion, success,
                success ? "Supervised řízení je aktivní." : "Virtual Stick nelze zapnout: " + errorText(error));
        });
    }

    @Override public void sendCommand(FlightCommand command) {
        FlightController current = flightController;
        if (!virtualStickEnabled || current == null || command == null) return;
        current.sendVirtualStickFlightControlData(
            new FlightControlData(command.pitch, command.roll, command.yaw, command.vertical), null);
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastGimbalAt >= GIMBAL_INTERVAL_MILLIS) {
            lastGimbalAt = now;
            rotateGimbal(command.gimbalPitch);
        }
    }

    private void rotateGimbal(float pitchSpeed) {
        Gimbal current = gimbal;
        if (current == null) return;
        Rotation rotation = new Rotation.Builder().mode(RotationMode.SPEED)
            .pitch(pitchSpeed).roll(Rotation.NO_ROTATION).yaw(Rotation.NO_ROTATION).build();
        current.rotate(rotation, null);
    }

    @Override public synchronized void disableVirtualStick(String reason, Completion completion) {
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
        if (state.getSatelliteCount() < SafetySupervisor.MINIMUM_SATELLITES) return "Pro vzlet je potřeba alespoň osm satelitů.";
        if (signalPercent < SafetySupervisor.MINIMUM_SIGNAL_PERCENT) return "Rádiové spojení je pro vzlet příliš slabé.";
        if (!state.isHomeLocationSet()) return "Domovský bod zatím není uložen.";
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
        if (returnHome && !state.isHomeLocationSet()) return "Návrat domů nelze spustit bez uloženého domovského bodu.";
        if (returnHome && state.getSatelliteCount() < SafetySupervisor.MINIMUM_SATELLITES) return "Návrat domů vyžaduje spolehlivou GNSS polohu.";
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
        if (recording) {
            current.stopRecordVideo(error -> postCompletion(completion, error == null,
                error == null ? "Nahrávání zastaveno." : "Nahrávání nelze zastavit: " + errorText(error)));
            return;
        }
        current.setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, error -> {
            if (error != null) {
                postCompletion(completion, false, "Režim videa nelze nastavit: " + errorText(error));
                return;
            }
            current.startRecordVideo(startError -> postCompletion(completion, startError == null,
                startError == null ? "Nahrávání spuštěno." : "Nahrávání nelze spustit: " + errorText(startError)));
        });
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
        videoActive = false;
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
        if (state == null) {
            return new TelemetrySnapshot(registered, connected, product, "—", batteryPercent, 0,
                0f, 0f, 0f, signalPercent, false, false, false, false, "UNKNOWN");
        }
        float horizontal = (float)Math.hypot(state.getVelocityX(), state.getVelocityY());
        String wind = state.getFlightWindWarning() == null ? "UNKNOWN" : state.getFlightWindWarning().name();
        LocationCoordinate3D aircraftLocation = state.getAircraftLocation();
        LocationCoordinate2D homeLocation = state.getHomeLocation();
        double aircraftLatitude = aircraftLocation == null ? Double.NaN : aircraftLocation.getLatitude();
        double aircraftLongitude = aircraftLocation == null ? Double.NaN : aircraftLocation.getLongitude();
        double homeLatitude = homeLocation == null ? Double.NaN : homeLocation.getLatitude();
        double homeLongitude = homeLocation == null ? Double.NaN : homeLocation.getLongitude();
        float heading = state.getAttitude() == null ? 0f : (float)state.getAttitude().yaw;
        return new TelemetrySnapshot(registered, connected, product, state.getFlightModeString(),
            batteryPercent, state.getSatelliteCount(), altitude(state), horizontal, state.getVelocityZ(),
            signalPercent, state.areMotorsOn(), state.isFlying(), state.isFailsafeEnabled(),
            state.isGoingHome(), wind, aircraftLatitude, aircraftLongitude, homeLatitude, homeLongitude,
            heading, state.isHomeLocationSet());
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

    private void postAction() {
        AircraftAction value = aircraftAction;
        main.post(() -> { if (!closed && listener != null) listener.onAircraftAction(value.label, value != AircraftAction.NONE); });
    }

    private void postCompletion(Completion completion, boolean success, String message) {
        if (completion != null) main.post(() -> completion.onComplete(success, message));
    }

    private static String errorText(DJIError error) {
        if (error == null) return "neznámá chyba";
        String description = error.getDescription();
        return description == null || description.trim().isEmpty() ? error.toString() : description;
    }

    @Override public synchronized void close() {
        if (closed) return;
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
