package cz.dachman.drone.director;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Locale;

/** Landscape flight console for supervised DJI Mini 2 filming. */
public final class MainActivity extends Activity implements DroneSession.Listener,
        WorkerTracker.Listener, FlightRuntime.Listener, TextureView.SurfaceTextureListener {
    private static final int BACKGROUND = Color.rgb(5, 17, 24);
    private static final int PANEL = Color.rgb(11, 35, 46);
    private static final int PANEL_LIGHT = Color.rgb(17, 48, 61);
    private static final int CYAN = Color.rgb(36, 211, 195);
    private static final int GREEN = Color.rgb(43, 232, 171);
    private static final int ORANGE = Color.rgb(255, 171, 64);
    private static final int RED = Color.rgb(234, 77, 89);
    private static final int MUTED = Color.rgb(151, 177, 188);
    private static final int DJI_PERMISSION_REQUEST = 2107;
    private static final int FRAME_WIDTH = 384;
    private static final int FRAME_HEIGHT = 216;

    private enum PendingKind { NONE, MODE, TAKEOFF, LANDING, RETURN_HOME }

    private final ApprovalGate gate = new ApprovalGate();
    private final Handler main = new Handler(Looper.getMainLooper());
    private CancellationSignal authentication;
    private DjiConnection dji;
    private WorkerTracker tracker;
    private FlightRuntime runtime;
    private TextureView video;
    private TrackingOverlay overlay;
    private FlightPathView flightPath;
    private TextView videoPlaceholder;
    private TextView liveBadge;
    private TextView connectionText;
    private TextView telemetryText;
    private TextView statusText;
    private TextView aiText;
    private TextView targetText;
    private TextView heightText;
    private TextView commandText;
    private TextView pendingText;
    private TextView aircraftActionText;
    private LinearLayout approvalCard;
    private CheckBox readinessCheck;
    private Button approveButton;
    private Button workerOneButton;
    private Button workerTwoButton;
    private Button recordButton;
    private Button cancelActionButton;
    private Spinner profileSpinner;
    private SeekBar heightSeek;
    private PendingKind pendingKind = PendingKind.NONE;
    private FlightPlan pendingPlan;
    private TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();
    private boolean videoActive;
    private boolean sampling;
    private boolean aircraftActionActive;

    private final Runnable frameSampler = new Runnable() {
        @Override public void run() {
            if (!sampling) return;
            if (videoActive && video != null && video.isAvailable() && tracker != null
                    && tracker.isReady() && !tracker.isProcessing()) {
                try {
                    Bitmap frame = video.getBitmap(FRAME_WIDTH, FRAME_HEIGHT);
                    if (frame != null) tracker.analyze(frame);
                } catch (RuntimeException error) {
                    showStatus("Snímek pro AI nelze načíst: " + safe(error));
                }
            }
            main.postDelayed(this, 330L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        buildInterface();
        dji = new DjiConnection(this);
        runtime = new FlightRuntime(dji, this);
        tracker = new WorkerTracker(this, this);
        dji.setListener(this);
        if (video.isAvailable()) dji.attachVideo(video.getSurfaceTexture(), video.getWidth(), video.getHeight());
        dji.connect();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout top = row();
        LinearLayout titles = column();
        TextView title = text("DACHMAN  AI  DRONE  DIRECTOR", 21, Color.WHITE, true);
        TextView subtitle = text("DJI MINI 2  •  SUPERVISED FLIGHT", 11, CYAN, true);
        titles.addView(title);
        titles.addView(subtitle);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button connect = button("PŘIPOJIT DJI", CYAN, () -> dji.connect());
        top.addView(connect, buttonParams());
        recordButton = button("● REC", RED, () -> dji.toggleRecording(this::showCompletion));
        top.addView(recordButton, buttonParams());
        Button photo = button("FOTO", PANEL_LIGHT, () -> dji.takePhoto(this::showCompletion));
        top.addView(photo, buttonParams());
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        telemetryText = text("SDK —   DRON —   BATERIE —   GNSS —   SIGNÁL —   VÝŠKA —", 12, Color.WHITE, true);
        telemetryText.setGravity(Gravity.CENTER_VERTICAL);
        telemetryText.setPadding(dp(10), 0, dp(10), 0);
        telemetryText.setBackground(card(PANEL, 10, CYAN));
        root.addView(telemetryText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        LinearLayout content = row();
        LinearLayout videoColumn = column();
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.62f);
        leftParams.setMargins(0, dp(9), dp(9), 0);
        content.addView(videoColumn, leftParams);

        FrameLayout videoFrame = new FrameLayout(this);
        videoFrame.setBackground(card(Color.BLACK, 12, PANEL_LIGHT));
        video = new TextureView(this);
        video.setSurfaceTextureListener(this);
        videoFrame.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoPlaceholder = text("ČEKÁM NA ŽIVÝ OBRAZ Z MINI 2", 15, MUTED, true);
        videoPlaceholder.setGravity(Gravity.CENTER);
        videoFrame.addView(videoPlaceholder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new TrackingOverlay(this);
        overlay.setTapListener(this::selectTarget);
        videoFrame.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        liveBadge = text("VIDEO OFF", 11, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setBackground(card(RED, 12, RED));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(92), dp(28), Gravity.TOP | Gravity.RIGHT);
        badgeParams.setMargins(0, dp(10), dp(10), 0);
        videoFrame.addView(liveBadge, badgeParams);
        videoColumn.addView(videoFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout beneathVideo = row();
        flightPath = new FlightPathView(this);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0, dp(122), 0.58f);
        pathParams.setMargins(0, dp(8), dp(8), 0);
        beneathVideo.addView(flightPath, pathParams);
        LinearLayout statePanel = column();
        statePanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        statePanel.setBackground(card(PANEL, 10, PANEL_LIGHT));
        statusText = text("PREFLIGHT – čekám na připojení", 14, Color.WHITE, true);
        commandText = text("PITCH 0,00   ROLL 0,00   YAW 0,0   VERT 0,00", 11, MUTED, false);
        connectionText = text("DJI SDK se připravuje…", 11, MUTED, false);
        aircraftActionText = text("AUTOMATICKÁ AKCE: ŽÁDNÁ", 11, ORANGE, true);
        statePanel.addView(statusText);
        statePanel.addView(commandText);
        statePanel.addView(connectionText);
        statePanel.addView(aircraftActionText);
        beneathVideo.addView(statePanel, new LinearLayout.LayoutParams(0, dp(122), 0.42f));
        videoColumn.addView(beneathVideo);

        ScrollView controlScroll = new ScrollView(this);
        controlScroll.setFillViewport(true);
        controlScroll.setVerticalScrollBarEnabled(false);
        LinearLayout controls = column();
        controls.setPadding(dp(12), dp(8), dp(12), dp(16));
        controls.setBackground(card(PANEL, 12, PANEL_LIGHT));
        controlScroll.addView(controls);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightParams.setMargins(0, dp(9), 0, 0);
        content.addView(controlScroll, rightParams);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        section(controls, "CÍL AI");
        aiText = text("AI MODEL SE NAČÍTÁ…", 11, MUTED, true);
        targetText = text("Worker 1: —   Worker 2: —", 12, Color.WHITE, false);
        controls.addView(aiText);
        controls.addView(targetText);
        LinearLayout workerRow = row();
        workerOneButton = button("VYBRAT WORKER 1", GREEN, () -> selectWorkerSlot(1));
        workerTwoButton = button("VYBRAT WORKER 2", ORANGE, () -> selectWorkerSlot(2));
        workerRow.addView(workerOneButton, weightedButtonParams());
        workerRow.addView(workerTwoButton, weightedButtonParams());
        controls.addView(workerRow);
        Button clearWorkers = button("ZRUŠIT VÝBĚR PRACOVNÍKŮ", PANEL_LIGHT, () -> {
            tracker.clearSelections();
            invalidatePending("Výběr cílů zrušen – HOLD");
        });
        controls.addView(clearWorkers, fullButtonParams());
        selectWorkerSlot(1);

        section(controls, "REŽIM LETU");
        GridLayout modeGrid = grid();
        addMode(modeGrid, FlightMode.STATIC_TRACK);
        addMode(modeGrid, FlightMode.FOLLOW);
        addMode(modeGrid, FlightMode.DUO_FOLLOW);
        addMode(modeGrid, FlightMode.ORBIT_LEFT);
        addMode(modeGrid, FlightMode.ORBIT_RIGHT);
        addMode(modeGrid, FlightMode.PULL_AWAY);
        addMode(modeGrid, FlightMode.REVEAL_UP);
        addMode(modeGrid, FlightMode.ROPE_MODE);
        controls.addView(modeGrid);

        section(controls, "RYCHLOST A VÝŠKOVÝ LIMIT");
        profileSpinner = new Spinner(this);
        profileSpinner.setAdapter(new DarkSpinnerAdapter<>(this, FlightProfile.values()));
        profileSpinner.setSelection(1);
        profileSpinner.setBackgroundTintList(ColorStateList.valueOf(CYAN));
        controls.addView(profileSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        heightText = text("VÝŠKOVÝ LIMIT – 15 m", 12, Color.WHITE, true);
        controls.addView(heightText);
        heightSeek = new SeekBar(this);
        heightSeek.setMin(FlightLevel.MINIMUM_METERS);
        heightSeek.setMax(FlightLevel.MAXIMUM_METERS);
        heightSeek.setProgress(15);
        heightSeek.setProgressTintList(ColorStateList.valueOf(CYAN));
        heightSeek.setThumbTintList(ColorStateList.valueOf(CYAN));
        heightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                heightText.setText("VÝŠKOVÝ LIMIT – " + value + " m");
                if (fromUser) invalidatePending("Výškový limit změněn – vyber znovu manévr.");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controls.addView(heightSeek);
        TextView levelHint = text("Volitelně po jednom metru. Jde o strop řízení, nikoli příkaz vystoupat.", 10, MUTED, false);
        controls.addView(levelHint);

        approvalCard = column();
        approvalCard.setPadding(dp(10), dp(9), dp(10), dp(9));
        approvalCard.setBackground(card(Color.rgb(23, 57, 67), 10, CYAN));
        pendingText = text("", 12, Color.WHITE, true);
        readinessCheck = new CheckBox(this);
        readinessCheck.setTextColor(Color.WHITE);
        readinessCheck.setButtonTintList(ColorStateList.valueOf(ORANGE));
        readinessCheck.setText("Potvrzuji volný prostor a přímý dohled na dron.");
        readinessCheck.setOnCheckedChangeListener((button, checked) -> updateApprovalButton());
        approveButton = button("OVĚŘIT A SPUSTIT", CYAN, this::authenticate);
        approvalCard.addView(pendingText);
        approvalCard.addView(readinessCheck);
        approvalCard.addView(approveButton, fullButtonParams());
        approvalCard.setVisibility(View.GONE);
        controls.addView(approvalCard);

        section(controls, "AUTONOMNÍ AKCE DRONU");
        GridLayout aircraftGrid = grid();
        addGridButton(aircraftGrid, "VZLET", GREEN, () -> requestAircraftAction(PendingKind.TAKEOFF));
        addGridButton(aircraftGrid, "PŘISTÁNÍ", ORANGE, () -> requestAircraftAction(PendingKind.LANDING));
        addGridButton(aircraftGrid, "NÁVRAT DOMŮ", ORANGE, () -> requestAircraftAction(PendingKind.RETURN_HOME));
        cancelActionButton = button("ZRUŠIT AUTO AKCI", RED, () -> dji.cancelAircraftAction(this::showCompletion));
        cancelActionButton.setEnabled(false);
        addGridView(aircraftGrid, cancelActionButton);
        controls.addView(aircraftGrid);

        LinearLayout stopRow = row();
        Button hold = button("HOLD", ORANGE, this::hold);
        Button abort = button("ABORT", RED, this::abort);
        stopRow.addView(hold, weightedButtonParams());
        stopRow.addView(abort, weightedButtonParams());
        controls.addView(stopRow);
        TextView safety = text("Kniply RC-N1 mají vždy přednost. Mini 2 nemá všesměrové vyhýbání překážkám; pilot odpovídá za volnou trasu.", 10, MUTED, false);
        safety.setPadding(0, dp(6), 0, 0);
        controls.addView(safety);
    }

    private void addMode(GridLayout grid, FlightMode mode) {
        addGridButton(grid, mode.label, PANEL_LIGHT, () -> requestMode(mode));
    }

    private void addGridButton(GridLayout grid, String label, int color, Runnable action) {
        addGridView(grid, button(label, color, action));
    }

    private void addGridView(GridLayout grid, View view) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(49);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(view, params);
    }

    private void requestMode(FlightMode mode) {
        if (tracker == null) return;
        TrackingSnapshot snapshot = tracker.snapshot();
        if (!snapshot.hasRequiredTargets(mode)) {
            showStatus(mode.requiresSecondary
                ? "Nejprve označ Worker 1 i Worker 2 v živém obrazu."
                : "Nejprve označ Worker 1 v živém obrazu.");
            return;
        }
        if (runtime.isActive()) runtime.hold("Změna manévru – HOLD");
        invalidatePending(null);
        FlightProfile profile = (FlightProfile) profileSpinner.getSelectedItem();
        pendingPlan = new FlightPlan(mode, FlightLevel.ofMeters(heightSeek.getProgress()), profile);
        pendingKind = PendingKind.MODE;
        gate.request(pendingPlan.approvalText());
        flightPath.setPlan(pendingPlan);
        pendingText.setText("NÁHLED TRAJEKTORIE\n" + pendingPlan.approvalText());
        readinessCheck.setVisibility(View.GONE);
        readinessCheck.setChecked(false);
        approvalCard.setVisibility(View.VISIBLE);
        updateApprovalButton();
        showStatus("Zkontroluj animovaný náhled a potvrď manévr.");
    }

    private void requestAircraftAction(PendingKind kind) {
        if (runtime != null && runtime.isActive()) runtime.abort("Příprava automatické akce – HOLD");
        invalidatePending(null);
        pendingKind = kind;
        String description;
        if (kind == PendingKind.TAKEOFF) {
            description = "AUTONOMNÍ VZLET\nDron spustí motory a vystoupá přibližně do výšky visu DJI.";
        } else if (kind == PendingKind.LANDING) {
            description = "AUTONOMNÍ PŘISTÁNÍ\nAplikace potvrdí i závěrečné dosednutí pod třicet centimetrů.";
        } else {
            description = "NÁVRAT DOMŮ\nDron použije uložený domovský bod a nastavenou DJI RTH výšku.";
        }
        gate.request(description.replace('\n', ' '));
        pendingText.setText(description);
        readinessCheck.setVisibility(View.VISIBLE);
        readinessCheck.setChecked(false);
        approvalCard.setVisibility(View.VISIBLE);
        flightPath.setPlan(new FlightPlan(FlightMode.HOLD,
            FlightLevel.ofMeters(heightSeek.getProgress()), (FlightProfile)profileSpinner.getSelectedItem()));
        updateApprovalButton();
        showStatus("Před ověřením zkontroluj prostor, domovský bod a stav dronu.");
    }

    private void authenticate() {
        if (gate.pending() == null || pendingKind == PendingKind.NONE || authentication != null) return;
        int methods = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        BiometricManager manager = getSystemService(BiometricManager.class);
        if (manager == null || manager.canAuthenticate(methods) != BiometricManager.BIOMETRIC_SUCCESS) {
            showStatus("Spuštění je zablokováno. Nastav kód zařízení nebo silnou biometrii.");
            return;
        }
        final long challenge = gate.revision();
        final PendingKind approvedKind = pendingKind;
        final FlightPlan approvedPlan = pendingPlan;
        authentication = new CancellationSignal();
        updateApprovalButton();
        new BiometricPrompt.Builder(this)
            .setTitle("Potvrdit řízení DJI Mini 2")
            .setDescription(gate.pending())
            .setAllowedAuthenticators(methods)
            .build()
            .authenticate(authentication, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    authentication = null;
                    if (challenge != gate.revision() || !gate.approve(challenge, true)) return;
                    clearPendingUi();
                    executeApproved(approvedKind, approvedPlan);
                }

                @Override public void onAuthenticationError(int code, CharSequence error) {
                    if (challenge != gate.revision()) return;
                    authentication = null;
                    showStatus("Akce nebyla potvrzena: " + error);
                    updateApprovalButton();
                }

                @Override public void onAuthenticationFailed() {
                    showStatus("Ověření se nezdařilo; zkus to znovu.");
                }
            });
    }

    private void executeApproved(PendingKind kind, FlightPlan plan) {
        if (kind == PendingKind.MODE && plan != null) {
            runtime.start(plan, this::showCompletion);
        } else if (kind == PendingKind.TAKEOFF) {
            runtime.abort("Autonomní vzlet – Virtual Stick vypnut");
            dji.startTakeoff(this::showCompletion);
        } else if (kind == PendingKind.LANDING) {
            runtime.abort("Autonomní přistání – Virtual Stick vypnut");
            dji.startLanding(this::showCompletion);
        } else if (kind == PendingKind.RETURN_HOME) {
            runtime.abort("Návrat domů – Virtual Stick vypnut");
            dji.startReturnHome(this::showCompletion);
        }
    }

    private void selectTarget(float x, float y, int slot) {
        if (tracker == null) return;
        if (tracker.selectAt(slot, x, y)) {
            invalidatePending("Worker " + slot + " potvrzen; případný starý manévr byl zrušen.");
            if (slot == 1 && tracker.snapshot().secondary == null) selectWorkerSlot(2);
        } else {
            showStatus("V místě klepnutí není jednoznačně rozpoznaná osoba.");
        }
    }

    private void selectWorkerSlot(int slot) {
        if (overlay == null) return;
        overlay.setSelectionSlot(slot);
        styleButton(workerOneButton, slot == 1 ? GREEN : PANEL_LIGHT);
        styleButton(workerTwoButton, slot == 2 ? ORANGE : PANEL_LIGHT);
    }

    private void hold() {
        invalidatePending(null);
        if (runtime != null) runtime.hold("HOLD – AI řízení vypnuto");
        showStatus("HOLD – dron drží pilot nebo letový kontrolér DJI.");
    }

    private void abort() {
        invalidatePending(null);
        if (runtime != null) runtime.abort("ABORT – AI řízení vypnuto");
        if (aircraftActionActive) dji.cancelAircraftAction(this::showCompletion);
        else showStatus("ABORT – AI řízení je vypnuté.");
    }

    private void invalidatePending(String message) {
        gate.cancel();
        if (authentication != null) {
            authentication.cancel();
            authentication = null;
        }
        clearPendingUi();
        if (message != null) showStatus(message);
    }

    private void clearPendingUi() {
        pendingKind = PendingKind.NONE;
        pendingPlan = null;
        if (approvalCard != null) approvalCard.setVisibility(View.GONE);
        if (readinessCheck != null) readinessCheck.setChecked(false);
    }

    private void updateApprovalButton() {
        if (approveButton == null) return;
        boolean needsChecklist = pendingKind == PendingKind.TAKEOFF
            || pendingKind == PendingKind.LANDING || pendingKind == PendingKind.RETURN_HOME;
        approveButton.setEnabled(authentication == null && pendingKind != PendingKind.NONE
            && (!needsChecklist || readinessCheck.isChecked()));
        approveButton.setText(authentication == null ? "OVĚŘIT A SPUSTIT" : "OVĚŘOVÁNÍ…");
    }

    private void showCompletion(boolean success, String message) {
        showStatus((success ? "✓ " : "! ") + message);
    }

    @Override public void onStatus(String message) { showStatus(message); }

    @Override public void onTelemetry(TelemetrySnapshot value) {
        telemetry = value;
        if (runtime != null) runtime.updateTelemetry(value);
        ui(() -> {
            String battery = value.batteryPercent < 0 ? "—" : value.batteryPercent + "%";
            String signal = value.signalPercent < 0 ? "—" : value.signalPercent + "%";
            telemetryText.setText(String.format(Locale.getDefault(),
                "SDK %s   DRON %s   BATERIE %s   GNSS %d   SIGNÁL %s   VÝŠKA %.1f m   RYCHLOST %.1f m/s",
                value.sdkRegistered ? "OK" : "—", value.connected ? "OK" : "—", battery,
                value.satellites, signal, value.altitudeMeters, value.horizontalSpeedMetersPerSecond));
        });
    }

    @Override public void onVideoState(boolean active) {
        videoActive = active;
        ui(() -> {
            videoPlaceholder.setVisibility(active ? View.GONE : View.VISIBLE);
            liveBadge.setText(active ? "● LIVE" : "VIDEO OFF");
            liveBadge.setBackground(card(active ? GREEN : RED, 12, active ? GREEN : RED));
            liveBadge.setTextColor(active ? BACKGROUND : Color.WHITE);
        });
    }

    @Override public void onPilotOverride(String reason) {
        ui(() -> {
            invalidatePending(null);
            runtime.abort(reason);
            showStatus(reason);
        });
    }

    @Override public void onCameraState(boolean recording) {
        ui(() -> {
            recordButton.setText(recording ? "■ STOP" : "● REC");
            styleButton(recordButton, recording ? ORANGE : RED);
        });
    }

    @Override public void onAircraftAction(String action, boolean active) {
        aircraftActionActive = active;
        ui(() -> {
            aircraftActionText.setText("AUTOMATICKÁ AKCE: " + action);
            aircraftActionText.setTextColor(active ? ORANGE : MUTED);
            cancelActionButton.setEnabled(active);
        });
    }

    @Override public void onTrackerReady() {
        ui(() -> {
            aiText.setText("AI DETEKCE OSOB: PŘIPRAVENA");
            aiText.setTextColor(GREEN);
        });
    }

    @Override public void onTracking(TrackingSnapshot snapshot) {
        if (runtime != null) runtime.updateTracking(snapshot);
        ui(() -> {
            overlay.setSnapshot(snapshot);
            targetText.setText("Worker 1: " + targetLabel(snapshot.primary)
                + "   Worker 2: " + targetLabel(snapshot.secondary)
                + "   Osoby: " + snapshot.candidates.size());
        });
    }

    @Override public void onTrackerError(String message) {
        ui(() -> {
            aiText.setText("AI DETEKCE: CHYBA");
            aiText.setTextColor(RED);
            showStatus(message);
        });
    }

    @Override public void onRuntimeState(boolean active, String message, FlightCommand command) {
        ui(() -> {
            statusText.setText(message);
            statusText.setTextColor(active ? GREEN : ORANGE);
            commandText.setText(String.format(Locale.getDefault(),
                "PITCH %.2f   ROLL %.2f   YAW %.1f   VERT %.2f   GIMBAL %.1f",
                command.pitch, command.roll, command.yaw, command.vertical, command.gimbalPitch));
        });
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (dji != null) dji.attachVideo(texture, width, height);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (dji != null) dji.attachVideo(texture, width, height);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        if (dji != null) dji.detachVideo();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != DJI_PERMISSION_REQUEST) return;
        boolean granted = results.length > 0;
        for (int result : results) granted &= result == PackageManager.PERMISSION_GRANTED;
        if (granted) dji.connect();
        else showStatus("Bez udělených oprávnění se DJI SDK nemůže připojit.");
    }

    @Override protected void onResume() {
        super.onResume();
        sampling = true;
        main.removeCallbacks(frameSampler);
        main.post(frameSampler);
    }

    @Override protected void onPause() {
        sampling = false;
        main.removeCallbacks(frameSampler);
        if (runtime != null) runtime.hold("HOLD – aplikace není v popředí");
        super.onPause();
    }

    @Override protected void onStop() {
        invalidatePending(null);
        super.onStop();
    }

    @Override protected void onDestroy() {
        sampling = false;
        main.removeCallbacksAndMessages(null);
        if (tracker != null) tracker.close();
        if (runtime != null) runtime.close();
        if (dji != null) dji.close();
        super.onDestroy();
    }

    private void showStatus(String message) {
        ui(() -> {
            if (statusText != null) statusText.setText(message);
            if (connectionText != null) connectionText.setText(message);
        });
    }

    private void ui(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else runOnUiThread(action);
    }

    private static String targetLabel(TargetBox box) {
        return box == null ? "—" : Math.round(box.confidence * 100f) + "%";
    }

    private static String safe(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void section(LinearLayout parent, String label) {
        TextView view = text(label, 11, CYAN, true);
        view.setPadding(0, dp(12), 0, dp(4));
        parent.addView(view);
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GridLayout grid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        return grid;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String label, int color, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setTextColor(color == CYAN || color == GREEN || color == ORANGE ? BACKGROUND : Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(view -> action.run());
        styleButton(button, color);
        return button;
    }

    private void styleButton(Button button, int color) {
        if (button == null) return;
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(color == CYAN || color == GREEN || color == ORANGE ? BACKGROUND : Color.WHITE);
        button.setAlpha(button.isEnabled() ? 1f : 0.45f);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(47), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(47));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private GradientDrawable card(int fill, float radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class DarkSpinnerAdapter<T> extends ArrayAdapter<T> {
        DarkSpinnerAdapter(Context context, T[] values) {
            super(context, android.R.layout.simple_spinner_item, values);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextColor(Color.WHITE);
            view.setTextSize(12);
            return view;
        }

        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            view.setTextColor(Color.WHITE);
            view.setBackgroundColor(PANEL_LIGHT);
            view.setPadding(20, 18, 20, 18);
            return view;
        }
    }
}
