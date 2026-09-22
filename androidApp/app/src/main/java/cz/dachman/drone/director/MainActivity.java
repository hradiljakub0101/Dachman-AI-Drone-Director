package cz.dachman.drone.director;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.usb.UsbManager;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Landscape, camera-first flight console for supervised DJI Mini 2 filming. */
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
    private static final int CONTROL_DRAWER_DP = 360;
    private static final int MAP_DRAWER_DP = 390;

    private enum PendingKind { NONE, MODE, TAKEOFF, EMERGENCY_LANDING, RETURN_HOME, FULL_MISSION }

    private final ApprovalGate gate = new ApprovalGate();
    private final ControlAuthority authority = new ControlAuthority();
    private final HudPanelState hudPanels = new HudPanelState();
    private final Handler main = new Handler(Looper.getMainLooper());
    private CancellationSignal authentication;
    private DjiConnection dji;
    private WorkerTracker tracker;
    private FlightRuntime runtime;
    private TextureView video;
    private TrackingOverlay overlay;
    private FlightMapView flightMap;
    private FlightRadarView flightRadar;
    private TextView videoPlaceholder;
    private TextView liveBadge;
    private TextView connectionText;
    private TextView telemetryText;
    private TextView statusText;
    private TextView aiText;
    private TextView targetText;
    private TextView safetyCalibrationText;
    private TextView heightText;
    private TextView commandText;
    private TextView pendingText;
    private TextView aircraftActionText;
    private TextView authorityText;
    private TextView cameraDirectorText;
    private TextView cameraStorageBadge;
    private TextView returnHomeText;
    private TextView rthHeightText;
    private LinearLayout approvalCard;
    private CheckBox readinessCheck;
    private Button approveButton;
    private Button workerOneButton;
    private Button workerTwoButton;
    private Button recordButton;
    private Button cancelActionButton;
    private Button rearmAiButton;
    private FrameLayout controlDrawer;
    private FrameLayout mapDrawer;
    private Button controlDrawerHandle;
    private Button mapDrawerHandle;
    private Spinner profileSpinner;
    private SeekBar heightSeek;
    private SeekBar rthHeightSeek;
    private SeekBar standoffSeek;
    private SeekBar roofHeightSeek;
    private SeekBar roofSlopeSeek;
    private SeekBar roofBearingSeek;
    private PendingKind pendingKind = PendingKind.NONE;
    private FlightPlan pendingPlan;
    private TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();
    private boolean videoActive;
    private boolean sampling;
    private boolean aircraftActionActive;
    private boolean cameraAutomationActive;
    private boolean cameraRecording;
    private CameraStorageStatus cameraStorageStatus = CameraStorageStatus.disconnected();
    private ReturnHomeStatus returnHomeStatus = ReturnHomeStatus.missing(
        "Před vzletem ulož návratový bod.");
    private boolean cameraZoomAvailable;
    private float cameraZoomFactor = CameraDirector.MIN_DIGITAL_ZOOM;
    /** Guided mission: every composition still requires biometric approval and can be aborted by RC. */
    private boolean missionActive;
    private FlightAuditLog auditLog;
    private final List<SiteSafetyPlan.Point> roofBoundary = new ArrayList<>();
    private final List<SiteSafetyPlan.Point> forbiddenZone = new ArrayList<>();
    private volatile BuildingSpatialModel latestSpatialModel;
    private boolean standoffCalibrated;
    private int missionIndex;
    private final FlightMode[] missionModes = new FlightMode[] {
        FlightMode.FOLLOW, FlightMode.ORBIT_RIGHT, FlightMode.PULL_AWAY, FlightMode.REVEAL_UP
    };
    private final Runnable missionPrompt = () -> {
        if (!missionActive) return;
        FlightMode mode = missionModes[missionIndex];
        FlightPlan plan = new FlightPlan(mode, FlightLevel.ofMeters(heightSeek.getProgress()),
            (FlightProfile) profileSpinner.getSelectedItem());
        showStatus("AI spouští kompozici " + (missionIndex + 1) + " z " + missionModes.length
            + ": " + mode.label + ". Pilot může kdykoli převzít řízení.");
        executeApproved(PendingKind.MODE, plan);
    };

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
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowManager.LayoutParams windowParams = getWindow().getAttributes();
        windowParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(windowParams);
        enterImmersiveMode();
        buildInterface();
        auditLog = new FlightAuditLog(getFilesDir());
        dji = new DjiConnection(this);
        runtime = new FlightRuntime(dji, this);
        tracker = new WorkerTracker(this, this);
        dji.setListener(this);
        if (video.isAvailable()) dji.attachVideo(video.getSurfaceTexture(), video.getWidth(), video.getHeight());
        if (!handleUsbIntent(getIntent())) dji.connect();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleUsbIntent(intent) && dji != null) dji.connect();
    }

    private boolean handleUsbIntent(Intent intent) {
        if (intent == null || !UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            return false;
        }
        if (dji != null) dji.onUsbAccessoryAttached();
        return true;
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);

        FrameLayout cameraArea = new FrameLayout(this);
        FrameLayout.LayoutParams cameraAreaParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(cameraArea, cameraAreaParams);

        FrameLayout videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        cameraArea.addView(videoFrame, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        video = new TextureView(this);
        video.setSurfaceTextureListener(this);
        videoFrame.addView(video, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        videoPlaceholder = text("ČEKÁM NA ŽIVÝ OBRAZ Z MINI 2", 15, MUTED, true);
        videoPlaceholder.setGravity(Gravity.CENTER);
        videoPlaceholder.setBackgroundColor(Color.BLACK);
        videoFrame.addView(videoPlaceholder, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new TrackingOverlay(this);
        overlay.setTapListener(this::selectTarget);
        videoFrame.addView(overlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topHud = row();
        topHud.setPadding(dp(9), dp(5), dp(9), dp(5));
        topHud.setBackground(card(Color.argb(115, 5, 17, 24), 8, Color.argb(150, 36, 211, 195)));
        LinearLayout titles = column();
        TextView title = text("DACHMAN AI", 17, Color.WHITE, true);
        TextView subtitle = text("DRONE DIRECTOR • MINI 2", 9, GREEN, true);
        titles.addView(title);
        titles.addView(subtitle);
        topHud.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button connect = button("DJI", CYAN, () -> dji.connect());
        topHud.addView(connect, compactButtonParams(dp(52)));
        recordButton = button("● REC", RED, () -> dji.toggleRecording(this::showCompletion));
        topHud.addView(recordButton, compactButtonParams(dp(58)));

        cameraStorageBadge = text("SD —", 8, Color.WHITE, true);
        cameraStorageBadge.setGravity(Gravity.CENTER);
        cameraStorageBadge.setBackground(card(Color.argb(190, 17, 48, 61), 8, MUTED));
        cameraStorageBadge.setOnClickListener(view -> showStatus(cameraStorageStatus.detail));
        LinearLayout.LayoutParams storageParams = new LinearLayout.LayoutParams(dp(52), dp(34));
        storageParams.setMargins(dp(4), 0, 0, 0);
        topHud.addView(cameraStorageBadge, storageParams);

        Button photo = button("FOTO", PANEL_LIGHT, () -> dji.takePhoto(this::showCompletion));
        topHud.addView(photo, compactButtonParams(dp(54)));

        liveBadge = text("OFF", 9, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setBackground(card(RED, 10, RED));
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(dp(44), dp(34));
        liveParams.setMargins(dp(4), 0, 0, 0);
        topHud.addView(liveBadge, liveParams);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            dp(430), dp(52), Gravity.TOP | Gravity.LEFT);
        topParams.setMargins(dp(8), dp(7), 0, 0);
        cameraArea.addView(topHud, topParams);

        telemetryText = text("SDK —  DRON —  BATERIE —  GNSS —  VÝŠKA —", 10, Color.WHITE, true);
        telemetryText.setGravity(Gravity.CENTER_VERTICAL);
        telemetryText.setPadding(dp(9), dp(3), dp(9), dp(3));
        telemetryText.setMaxLines(2);
        telemetryText.setBackground(card(Color.argb(105, 5, 17, 24), 8, Color.argb(155, 43, 232, 171)));
        FrameLayout.LayoutParams telemetryParams = new FrameLayout.LayoutParams(
            dp(430), dp(43), Gravity.TOP | Gravity.LEFT);
        telemetryParams.setMargins(dp(8), dp(64), 0, 0);
        cameraArea.addView(telemetryText, telemetryParams);

        authorityText = text("[MANUAL] → AI PŘIPRAVENA → AI AKTIVNÍ → PILOT PŘEVZAL → RTH",
            9, Color.WHITE, true);
        authorityText.setGravity(Gravity.CENTER);
        authorityText.setMaxLines(2);
        authorityText.setBackground(card(Color.argb(110, 5, 17, 24), 8, CYAN));
        FrameLayout.LayoutParams authorityParams = new FrameLayout.LayoutParams(
            dp(430), dp(36), Gravity.TOP | Gravity.LEFT);
        authorityParams.setMargins(dp(8), dp(112), 0, 0);
        cameraArea.addView(authorityText, authorityParams);

        cameraDirectorText = text("KAMERA AI • VYPNUTA", 9, MUTED, true);
        cameraDirectorText.setGravity(Gravity.CENTER_VERTICAL);
        cameraDirectorText.setPadding(dp(9), 0, dp(9), 0);
        cameraDirectorText.setBackground(card(Color.argb(105, 5, 17, 24), 8,
            Color.argb(145, 151, 177, 188)));
        FrameLayout.LayoutParams cameraDirectorParams = new FrameLayout.LayoutParams(
            dp(300), dp(29), Gravity.TOP | Gravity.LEFT);
        cameraDirectorParams.setMargins(dp(8), dp(153), 0, 0);
        cameraArea.addView(cameraDirectorText, cameraDirectorParams);

        flightRadar = new FlightRadarView(this);
        FrameLayout.LayoutParams radarParams = new FrameLayout.LayoutParams(
            dp(92), dp(100), Gravity.TOP | Gravity.RIGHT);
        radarParams.setMargins(0, dp(60), dp(12), 0);
        cameraArea.addView(flightRadar, radarParams);

        flightMap = new FlightMapView(this);
        flightMap.setAlpha(0.88f);
        flightMap.setSafetyPlanListener((roof, forbidden) -> {
            roofBoundary.clear(); roofBoundary.addAll(roof);
            forbiddenZone.clear(); forbiddenZone.addAll(forbidden);
            publishSafetyConfiguration();
        });

        mapDrawer = new FrameLayout(this);
        mapDrawer.setPadding(dp(7), dp(7), dp(7), dp(7));
        mapDrawer.setBackground(card(Color.argb(125, 5, 17, 24), 12, Color.argb(170, 36, 211, 195)));
        LinearLayout mapDrawerContent = row();
        LinearLayout.LayoutParams mapItemParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mapItemParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        mapDrawerContent.addView(flightMap, mapItemParams);
        mapDrawer.addView(mapDrawerContent, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams mapDrawerParams = new FrameLayout.LayoutParams(
            dp(MAP_DRAWER_DP), dp(164), Gravity.LEFT | Gravity.BOTTOM);
        mapDrawerParams.setMargins(dp(6), 0, 0, dp(64));
        root.addView(mapDrawer, mapDrawerParams);

        ScrollView controlScroll = new ScrollView(this);
        controlScroll.setFillViewport(true);
        controlScroll.setVerticalScrollBarEnabled(false);
        LinearLayout controls = column();
        controls.setPadding(dp(10), dp(7), dp(10), dp(14));
        controls.setBackground(card(Color.argb(150, 5, 17, 24), 12, Color.argb(170, 36, 211, 195)));
        controlScroll.addView(controls);
        controlDrawer = new FrameLayout(this);
        controlDrawer.addView(controlScroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
            dp(CONTROL_DRAWER_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        controlParams.setMargins(0, dp(4), dp(5), dp(64));
        root.addView(controlDrawer, controlParams);

        LinearLayout statePanel = column();
        statePanel.setPadding(dp(8), dp(5), dp(8), dp(5));
        statePanel.setBackground(card(Color.argb(125, 17, 48, 61), 9, CYAN));
        statusText = text("PREFLIGHT – čekám na připojení", 12, Color.WHITE, true);
        commandText = text("PITCH 0,00  ROLL 0,00  YAW 0,0  VERT 0,00", 9, MUTED, false);
        connectionText = text("DJI SDK se připravuje…", 9, MUTED, false);
        aircraftActionText = text("AUTOMATICKÁ AKCE: ŽÁDNÁ", 9, ORANGE, true);
        statePanel.addView(statusText);
        statePanel.addView(commandText);
        statePanel.addView(connectionText);
        statePanel.addView(aircraftActionText);
        controls.addView(statePanel);

        section(controls, "OBRAZOVÉ SLEDOVÁNÍ OSOB");
        aiText = text("AI MODEL SE NAČÍTÁ…", 10, MUTED, true);
        targetText = text("Worker 1: —   Worker 2: —", 10, Color.WHITE, false);
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
            invalidatePending("Obrazový výběr osob byl zrušen.");
        });
        controls.addView(clearWorkers, fullButtonParams());
        selectWorkerSlot(1);

        section(controls, "KALIBRACE ODSTUPU A PROSTOR STŘECHY");
        safetyCalibrationText = text("ODSTUP NENÍ KALIBROVÁN • výchozí šest metrů", 9, ORANGE, true);
        controls.addView(safetyCalibrationText);
        standoffSeek = new SeekBar(this);
        standoffSeek.setMin(6); standoffSeek.setMax(30); standoffSeek.setProgress(6);
        standoffSeek.setProgressTintList(ColorStateList.valueOf(ORANGE));
        standoffSeek.setOnSeekBarChangeListener(simpleSeek(() -> {
            standoffCalibrated = true; publishSafetyConfiguration();
        }));
        controls.addView(standoffSeek);
        LinearLayout mapEditRow = row();
        mapEditRow.addView(button("KRESLIT STŘECHU", GREEN,
            () -> flightMap.setEditorMode(FlightMapView.EditorMode.ROOF)), weightedButtonParams());
        mapEditRow.addView(button("KRESLIT ZÁKAZ", RED,
            () -> flightMap.setEditorMode(FlightMapView.EditorMode.FORBIDDEN)), weightedButtonParams());
        controls.addView(mapEditRow);
        controls.addView(button("UKONČIT KRESLENÍ", PANEL_LIGHT,
            () -> flightMap.setEditorMode(FlightMapView.EditorMode.NONE)), fullButtonParams());
        controls.addView(button("SMAZAT MAPOVÉ ZÓNY", PANEL_LIGHT, flightMap::clearSafetyPoints),
            fullButtonParams());
        roofHeightSeek = safetySeek(0, 50, 0, GREEN, controls, "VÝŠKA OKAPU NAD STARTEM");
        roofSlopeSeek = safetySeek(0, 60, 0, GREEN, controls, "SKLON STŘECHY");
        roofBearingSeek = safetySeek(0, 359, 0, GREEN, controls, "SMĚR SPÁDU");
        controls.addView(text("Polygon je volitelný. Po volbě vrstvy přidávej body běžným klepnutím do mapy.",
            8, MUTED, false));

        section(controls, "LETOVÝ PROTOKOL AI");
        controls.addView(text("Rozhodnutí, stav fúze a povely se ukládají do flight-ai-audit.jsonl.",
            8, MUTED, false));

        section(controls, "VŠECHNY REŽIMY LETU");
        GridLayout modeGrid = grid();
        modeGrid.setColumnCount(4);
        addMode(modeGrid, FlightMode.SURVEY_MAP);
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
        controls.addView(profileSpinner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        heightText = text("VÝŠKOVÝ LIMIT – 15 m", 11, Color.WHITE, true);
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
        controls.addView(text("Strop řízení, nikoli příkaz vystoupat.", 9, MUTED, false));

        section(controls, "NÁVRATOVÝ BOD – POVINNÉ PŘED VZLETEM");
        LinearLayout homeCard = column();
        homeCard.setPadding(dp(8), dp(7), dp(8), dp(7));
        homeCard.setBackground(card(Color.argb(145, 17, 48, 61), 9, ORANGE));
        returnHomeText = text("HOME — • polož dron na místo návratu", 10, Color.WHITE, true);
        homeCard.addView(returnHomeText);
        rthHeightText = text("RTH VÝŠKA – 30 m", 10, Color.WHITE, true);
        homeCard.addView(rthHeightText);
        rthHeightSeek = new SeekBar(this);
        rthHeightSeek.setMin(20);
        rthHeightSeek.setMax(120);
        rthHeightSeek.setProgress(30);
        rthHeightSeek.setProgressTintList(ColorStateList.valueOf(ORANGE));
        rthHeightSeek.setThumbTintList(ColorStateList.valueOf(ORANGE));
        rthHeightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                rthHeightText.setText("RTH VÝŠKA – " + value + " m");
                if (fromUser && returnHomeStatus.ready) {
                    renderReturnHomeStatus();
                    invalidatePending("RTH výška změněna – Home Point je nutné znovu ověřit.");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        homeCard.addView(rthHeightSeek);
        Button saveHome = button("ULOŽIT BOD VZLETU", GREEN,
            () -> dji.prepareReturnHome(rthHeightSeek.getProgress(), this::showCompletion));
        homeCard.addView(saveHome, fullButtonParams());
        controls.addView(homeCard);

        approvalCard = column();
        approvalCard.setPadding(dp(9), dp(8), dp(9), dp(8));
        approvalCard.setBackground(card(Color.argb(155, 23, 57, 67), 10, CYAN));
        pendingText = text("", 11, Color.WHITE, true);
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
        addGridButton(aircraftGrid, "NÁVRAT A PŘISTÁNÍ", ORANGE,
            () -> requestAircraftAction(PendingKind.RETURN_HOME));
        addGridButton(aircraftGrid, "AUTONOMNÍ MISE", CYAN, this::requestFullMission);
        Button emergencyLanding = button("DRŽET: NOUZOVĚ PŘISTÁT ZDE", RED,
            () -> showStatus("Nouzové přistání otevřeš dlouhým podržením tlačítka."));
        emergencyLanding.setOnLongClickListener(view -> {
            requestAircraftAction(PendingKind.EMERGENCY_LANDING);
            return true;
        });
        addGridView(aircraftGrid, emergencyLanding);
        cancelActionButton = button("ZRUŠIT AUTO AKCI", RED,
            () -> dji.cancelAircraftAction(this::showCompletion));
        cancelActionButton.setEnabled(false);
        addGridView(aircraftGrid, cancelActionButton);
        controls.addView(aircraftGrid);

        rearmAiButton = button("RUČNĚ PŘIPRAVIT AI ZNOVU", CYAN, this::confirmAiRearm);
        rearmAiButton.setVisibility(View.GONE);
        controls.addView(rearmAiButton, fullButtonParams());

        LinearLayout stopRow = row();
        stopRow.addView(button("HOLD", ORANGE, this::hold), weightedButtonParams());
        stopRow.addView(button("ABORT", RED, this::abort), weightedButtonParams());
        controls.addView(stopRow);
        TextView safety = text("Kniply i kolečko gimbalu RC-N1 mají vždy přednost. Pilot odpovídá za volnou trasu.", 9, MUTED, false);
        safety.setPadding(0, dp(5), 0, 0);
        controls.addView(safety);

        LinearLayout quickBar = row();
        quickBar.setPadding(dp(6), dp(4), dp(6), dp(5));
        quickBar.setBackground(card(Color.argb(105, 5, 17, 24), 8, Color.argb(155, 43, 232, 171)));
        quickBar.addView(button("FOLLOW", PANEL_LIGHT, () -> requestMode(FlightMode.FOLLOW)), weightedButtonParams());
        quickBar.addView(button("ORBIT", PANEL_LIGHT, () -> requestMode(FlightMode.ORBIT_RIGHT)), weightedButtonParams());
        quickBar.addView(button("ROPE", PANEL_LIGHT, () -> requestMode(FlightMode.ROPE_MODE)), weightedButtonParams());
        quickBar.addView(button("PULL", PANEL_LIGHT, () -> requestMode(FlightMode.PULL_AWAY)), weightedButtonParams());
        quickBar.addView(button("HOLD", ORANGE, this::hold), weightedButtonParams());
        quickBar.addView(button("ABORT", RED, this::abort), weightedButtonParams());
        cameraArea.addView(quickBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(61), Gravity.BOTTOM));

        controlDrawerHandle = button("☰ OVLÁDÁNÍ", CYAN, this::toggleControlDrawer);
        FrameLayout.LayoutParams controlHandleParams = new FrameLayout.LayoutParams(
            dp(112), dp(42), Gravity.TOP | Gravity.RIGHT);
        controlHandleParams.setMargins(0, dp(8), dp(8), 0);
        root.addView(controlDrawerHandle, controlHandleParams);

        mapDrawerHandle = button("MAPA + TRASA", CYAN, this::toggleMapDrawer);
        FrameLayout.LayoutParams mapHandleParams = new FrameLayout.LayoutParams(
            dp(112), dp(42), Gravity.LEFT | Gravity.BOTTOM);
        mapHandleParams.setMargins(dp(8), 0, 0, dp(68));
        root.addView(mapDrawerHandle, mapHandleParams);

        setContentView(root);
        updateAuthorityUi();
        hudPanels.openMap();
        root.post(() -> applyHudPanelState(false));
    }

    private void toggleControlDrawer() {
        hudPanels.toggleControls();
        applyHudPanelState(true);
    }

    private void toggleMapDrawer() {
        hudPanels.toggleMap();
        applyHudPanelState(true);
    }

    private void revealControlDrawer() {
        hudPanels.openControls();
        applyHudPanelState(true);
    }

    private void revealMapDrawer() {
        hudPanels.openMap();
        applyHudPanelState(true);
    }

    private void closeHudDrawers() {
        hudPanels.closeAll();
        applyHudPanelState(true);
    }

    private void applyHudPanelState(boolean animate) {
        if (controlDrawer == null || mapDrawer == null) return;
        float controlOffset = hudPanels.controlsOpen() ? 0f
            : Math.max(controlDrawer.getWidth(), dp(CONTROL_DRAWER_DP));
        float mapOffset = hudPanels.mapOpen() ? 0f
            : -Math.max(mapDrawer.getWidth(), dp(MAP_DRAWER_DP));
        float controlHandleOffset = hudPanels.controlsOpen() ? -dp(CONTROL_DRAWER_DP) : 0f;
        float mapHandleOffset = hudPanels.mapOpen() ? dp(MAP_DRAWER_DP) : 0f;
        if (animate) {
            controlDrawer.animate().translationX(controlOffset).setDuration(220L).start();
            mapDrawer.animate().translationX(mapOffset).setDuration(220L).start();
            controlDrawerHandle.animate().translationX(controlHandleOffset).setDuration(220L).start();
            mapDrawerHandle.animate().translationX(mapHandleOffset).setDuration(220L).start();
        } else {
            controlDrawer.setTranslationX(controlOffset);
            mapDrawer.setTranslationX(mapOffset);
            controlDrawerHandle.setTranslationX(controlHandleOffset);
            mapDrawerHandle.setTranslationX(mapHandleOffset);
        }
        controlDrawerHandle.setText(hudPanels.controlsOpen() ? "ZAVŘÍT ▶" : "☰ OVLÁDÁNÍ");
        mapDrawerHandle.setText(hudPanels.mapOpen() ? "◀ SKRÝT" : "MAPA + TRASA");
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
        if (authority.manualRearmRequired()) {
            showStatus("Pilot převzal řízení. Nejdřív stiskni RUČNĚ PŘIPRAVIT AI ZNOVU.");
            return;
        }
        if (mode.requiresVisualTarget() && (tracker == null
                || !tracker.snapshot().hasRequiredTargets(mode))) {
            showStatus(mode == FlightMode.DUO_FOLLOW
                ? "Nejprve v obrazu vyber Worker 1 a Worker 2."
                : "Nejprve v obrazu vyber sledovanou osobu.");
            return;
        }
        authority.targetConfirmed(true);
        updateAuthorityUi();
        if (runtime.isActive()) runtime.hold("Změna manévru – HOLD");
        invalidatePending(null);
        FlightProfile profile = (FlightProfile) profileSpinner.getSelectedItem();
        pendingPlan = new FlightPlan(mode, FlightLevel.ofMeters(heightSeek.getProgress()), profile);
        pendingKind = PendingKind.MODE;
        gate.request(pendingPlan.approvalText());
        pendingText.setText(pendingPlan.approvalText()
            + "\nKamera AI: centrování cíle, gimbal a podporovaný zoom.");
        readinessCheck.setVisibility(View.GONE);
        readinessCheck.setChecked(false);
        approvalCard.setVisibility(View.GONE);
        showStatus("Ověř vybraný režim jedním systémovým potvrzením.");
        authenticate();
    }

    private void requestAircraftAction(PendingKind kind) {
        if (runtime != null && runtime.isActive()) runtime.abort("Příprava automatické akce – HOLD");
        invalidatePending(null);
        pendingKind = kind;
        String description;
        if (kind == PendingKind.TAKEOFF) {
            description = "AUTONOMNÍ VZLET\nDron spustí motory a vystoupá přibližně do výšky visu DJI.";
        } else if (kind == PendingKind.EMERGENCY_LANDING) {
            description = "NOUZOVÉ PŘISTÁNÍ ZDE\nDron nepoletí domů a začne přistávat v aktuální poloze.";
        } else {
            description = "NÁVRAT A PŘISTÁNÍ\nDron použije ověřený Home Point a nastavenou DJI RTH výšku.";
        }
        gate.request(description.replace('\n', ' '));
        pendingText.setText(description);
        readinessCheck.setVisibility(View.GONE);
        approvalCard.setVisibility(View.GONE);
        showStatus("Zkontroluj prostor a potvrď akci jedním systémovým ověřením.");
        authenticate();
    }

    private void requestFullMission() {
        if (authority.manualRearmRequired()) {
            showStatus("Pilot převzal řízení. Nejdřív stiskni RUČNĚ PŘIPRAVIT AI ZNOVU.");
            return;
        }
        if (tracker == null || !tracker.snapshot().hasRequiredTargets(FlightMode.FOLLOW)) {
            showStatus("Před misí vyber sledovanou osobu v živém obrazu.");
            return;
        }
        authority.targetConfirmed(true);
        updateAuthorityUi();
        if (runtime != null && runtime.isActive()) runtime.abort("Příprava mise – HOLD");
        invalidatePending(null);
        pendingKind = PendingKind.FULL_MISSION;
        gate.request("AUTONOMNÍ MISE: vzlet, čtyři obrazově řízené kompozice a návrat domů");
        pendingText.setText("AUTONOMNÍ MISE\nVzlet → čtyři obrazově řízené kompozice → RTH.");
        pendingText.append("\nKamera AI průběžně řídí náklon a digitální zoom.");
        readinessCheck.setVisibility(View.GONE);
        approvalCard.setVisibility(View.GONE);
        showStatus("Zkontroluj prostor, Home Point a přímý dohled; potom proveď jedno ověření.");
        authenticate();
    }

    private void confirmAiRearm() {
        if (!authority.confirmManualRearm(true)) {
            showStatus("AI nelze připravit. Ověř připojení dronu a bezpečnostní stav.");
            return;
        }
        updateAuthorityUi();
        showStatus("AI je znovu připravena. Každý další manévr musíš ručně potvrdit.");
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
            if (!authority.activateAi()) {
                showStatus("AI řízení není připravené. Aktivuj je znovu ručně v APK.");
                updateAuthorityUi();
                return;
            }
            updateAuthorityUi();
            runtime.start(plan, (success, message) -> {
                showCompletion(success, message);
                if (!success) {
                    authority.stopToManual();
                    updateAuthorityUi();
                    missionActive = false;
                    return;
                }
                closeHudDrawers();
                if (plan.mode == FlightMode.SURVEY_MAP) {
                    main.postDelayed(() -> {
                        if (runtime != null && runtime.isActive()) {
                            runtime.hold("Mapovací oblet dokončen – prostorový model uložen");
                        }
                    }, 60_000L);
                }
                if (!missionActive) return;
                // Keep each shot bounded; then stop, re-check the target and ask for the next approval.
                main.postDelayed(() -> {
                    if (!missionActive) return;
                    runtime.hold("Kompozice dokončena – HOLD před dalším záběrem");
                    authority.compositionCompleted();
                    updateAuthorityUi();
                    missionIndex++;
                    if (missionIndex < missionModes.length) {
                        main.postDelayed(missionPrompt, 900L);
                    } else {
                        missionActive = false;
                        showStatus("Kompozice dokončeny. Spouštím předem schválený bezpečný návrat domů.");
                        executeApproved(PendingKind.RETURN_HOME, null);
                    }
                }, 8_000L);
            });
        } else if (kind == PendingKind.TAKEOFF) {
            runtime.abort("Autonomní vzlet – Virtual Stick vypnut");
            closeHudDrawers();
            dji.startTakeoff(this::showCompletion);
        } else if (kind == PendingKind.EMERGENCY_LANDING) {
            runtime.abort("Nouzové přistání – Virtual Stick vypnut");
            closeHudDrawers();
            dji.startLanding(this::showCompletion);
        } else if (kind == PendingKind.RETURN_HOME) {
            runtime.abort("Návrat domů – Virtual Stick vypnut");
            authority.beginRth(true);
            updateAuthorityUi();
            closeHudDrawers();
            dji.startReturnHome((success, message) -> {
                showCompletion(success, message);
                if (!success) {
                    authority.finishRth();
                    updateAuthorityUi();
                }
            });
        } else if (kind == PendingKind.FULL_MISSION) {
            missionActive = true;
            missionIndex = 0;
            runtime.abort("Autonomní mise – příprava vzletu");
            closeHudDrawers();
            dji.startTakeoff((success, message) -> {
                showCompletion(success, message);
                if (success) main.postDelayed(missionPrompt, 5_000L);
                else missionActive = false;
            });
        }
    }

    private void selectTarget(float x, float y, int slot) {
        if (tracker == null) return;
        if (tracker.selectAt(slot, x, y)) {
            boolean ready = slot != 1 || authority.targetConfirmed(true);
            updateAuthorityUi();
            invalidatePending("Worker " + slot + " potvrzen; "
                + (ready ? "AI je připravena." : "pilotní převzetí zůstává uzamčené."));
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
        missionActive = false;
        main.removeCallbacks(missionPrompt);
        invalidatePending(null);
        if (runtime != null) runtime.hold("HOLD – AI řízení vypnuto");
        authority.stopToManual();
        updateAuthorityUi();
        showStatus("HOLD – dron drží pilot nebo letový kontrolér DJI.");
    }

    private void abort() {
        missionActive = false;
        main.removeCallbacks(missionPrompt);
        invalidatePending(null);
        if (runtime != null) runtime.abort("ABORT – AI řízení vypnuto");
        authority.stopToManual();
        updateAuthorityUi();
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
            || pendingKind == PendingKind.EMERGENCY_LANDING || pendingKind == PendingKind.RETURN_HOME
            || pendingKind == PendingKind.FULL_MISSION;
        boolean needsVerifiedHome = pendingKind == PendingKind.TAKEOFF
            || pendingKind == PendingKind.FULL_MISSION || pendingKind == PendingKind.RETURN_HOME;
        approveButton.setEnabled(authentication == null && pendingKind != PendingKind.NONE
            && (!needsChecklist || readinessCheck.isChecked())
            && (!needsVerifiedHome || homeConfigurationReady()));
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
            String position = value.hasAircraftLocation()
                ? String.format(Locale.getDefault(), "%.5f, %.5f", value.aircraftLatitude, value.aircraftLongitude)
                : "GPS —";
            telemetryText.setText(String.format(Locale.getDefault(),
                "SDK %s  DRON %s  BAT %s  GNSS %d  SIGNÁL %s\nALT %.1f m  SPD %.1f m/s  %s",
                value.sdkRegistered ? "OK" : "—", value.connected ? "OK" : "—", battery,
                value.satellites, signal, value.altitudeMeters,
                value.horizontalSpeedMetersPerSecond, position));
            if (flightMap != null) flightMap.updateTelemetry(value);
            if (flightRadar != null) flightRadar.updateTelemetry(value);
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
            cameraAutomationActive = false;
            missionActive = false;
            main.removeCallbacks(missionPrompt);
            invalidatePending(null);
            authority.pilotTookOver();
            updateAuthorityUi();
            revealControlDrawer();
            runtime.abort(reason);
            showStatus(reason);
        });
    }

    @Override public void onCameraState(boolean recording) {
        ui(() -> {
            cameraRecording = recording;
            renderRecordingUi();
        });
    }

    @Override public void onCameraStorageState(CameraStorageStatus status) {
        if (status == null) return;
        ui(() -> {
            cameraStorageStatus = status;
            renderRecordingUi();
        });
    }

    @Override public void onReturnHomeStatus(ReturnHomeStatus status) {
        if (status == null) return;
        ui(() -> {
            returnHomeStatus = status;
            renderReturnHomeStatus();
            updateApprovalButton();
        });
    }

    private boolean homeConfigurationReady() {
        return returnHomeStatus.ready && rthHeightSeek != null
            && returnHomeStatus.rthHeightMeters == rthHeightSeek.getProgress();
    }

    private void renderReturnHomeStatus() {
        if (returnHomeText == null) return;
        if (returnHomeStatus.configuring) {
            returnHomeText.setText("HOME … • zapisuji a ověřuji bezpečnostní nastavení");
            returnHomeText.setTextColor(ORANGE);
            return;
        }
        if (!returnHomeStatus.ready) {
            returnHomeText.setText("HOME — • " + returnHomeStatus.detail);
            returnHomeText.setTextColor(RED);
            return;
        }
        String saved = DateFormat.getTimeInstance(DateFormat.SHORT).format(
            new Date(returnHomeStatus.savedAtEpochMillis));
        String distance = Double.isNaN(returnHomeStatus.distanceToHomeMeters) ? "—"
            : String.format(Locale.getDefault(), "%.1f m", returnHomeStatus.distanceToHomeMeters);
        boolean selectedHeightMatches = rthHeightSeek != null
            && rthHeightSeek.getProgress() == returnHomeStatus.rthHeightMeters;
        returnHomeText.setText(String.format(Locale.getDefault(),
            "HOME %s • %.5f, %.5f • vzdálenost %s • RTH %d m%s",
            saved, returnHomeStatus.latitude, returnHomeStatus.longitude, distance,
            returnHomeStatus.rthHeightMeters,
            selectedHeightMatches ? " • SMART + FAILSAFE OK" : " • ULOŽ ZNOVU"));
        returnHomeText.setTextColor(selectedHeightMatches ? GREEN : ORANGE);
    }

    private void renderRecordingUi() {
        boolean recordControlAvailable = cameraRecording || cameraStorageStatus.ready;
        recordButton.setEnabled(recordControlAvailable);
        recordButton.setText(cameraRecording ? "■ STOP" : "● REC");
        styleButton(recordButton, cameraRecording ? ORANGE
            : cameraStorageStatus.ready ? RED : PANEL_LIGHT);

        cameraStorageBadge.setText(cameraStorageStatus.shortLabel);
        cameraStorageBadge.setTextColor(cameraStorageStatus.ready ? BACKGROUND : Color.WHITE);
        int storageColor = cameraStorageStatus.ready ? GREEN
            : "SD …".equals(cameraStorageStatus.shortLabel) ? ORANGE : RED;
        cameraStorageBadge.setBackground(card(storageColor, 8, storageColor));
        cameraStorageBadge.setContentDescription(cameraStorageStatus.detail);
    }

    @Override public void onCameraAutomationState(boolean digitalZoomSupported,
            float appliedZoomFactor) {
        if (runtime != null) runtime.updateAppliedZoom(digitalZoomSupported, appliedZoomFactor);
        ui(() -> {
            cameraZoomAvailable = digitalZoomSupported;
            cameraZoomFactor = appliedZoomFactor;
            renderCameraDirectorUi();
        });
    }

    @Override public void onAircraftAction(String action, boolean active) {
        aircraftActionActive = active;
        ui(() -> {
            if (active && action.contains("NÁVRAT DOMŮ")) {
                authority.beginRth(true);
            } else if (!active && authority.state() == ControlAuthority.State.RTH) {
                authority.finishRth();
            }
            updateAuthorityUi();
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
            cameraAutomationActive = active;
            if (!active && authority.state() == ControlAuthority.State.AI_ACTIVE) {
                if (message.startsWith("Kompozice dokončena")) authority.compositionCompleted();
                else {
                    missionActive = false;
                    main.removeCallbacks(missionPrompt);
                    authority.stopToManual();
                }
                updateAuthorityUi();
            }
            statusText.setText(message);
            statusText.setTextColor(active ? GREEN : ORANGE);
            String zoom = Float.isFinite(command.digitalZoomFactor)
                ? String.format(Locale.getDefault(), "%.2f×", command.digitalZoomFactor) : "—";
            commandText.setText(String.format(Locale.getDefault(),
                "PITCH %.2f   ROLL %.2f   YAW %.1f   VERT %.2f   GIMBAL %.1f   ZOOM %s",
                command.pitch, command.roll, command.yaw, command.vertical,
                command.gimbalPitch, zoom));
            renderCameraDirectorUi();
            FusionStatus fusion = currentFusionStatus();
            if (auditLog != null) auditLog.append(active ? "AI_COMMAND" : "AI_STATE", message, fusion, command);
        });
    }

    @Override public void onSpatialModelUpdated(BuildingSpatialModel model) {
        if (model == null) return;
        latestSpatialModel = model;
        ui(() -> {
            String state = model.ready() ? "MODEL PŘIPRAVEN" : "MODEL SE ZPŘESŇUJE";
            showStatus(state + " • pokrytí " + model.coveredSectors + "/8 • jistota "
                + model.confidencePercent() + " %");
            if (auditLog != null) auditLog.append("SPATIAL_MODEL",
                state + ", pozorování=" + model.observations + ", sektory=" + model.coveredSectors,
                currentFusionStatus(), FlightCommand.ZERO);
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
        if (flightMap != null) flightMap.onHostResume();
        sampling = true;
        main.removeCallbacks(frameSampler);
        main.post(frameSampler);
    }

    @Override protected void onPause() {
        sampling = false;
        main.removeCallbacks(frameSampler);
        missionActive = false;
        main.removeCallbacks(missionPrompt);
        if (flightMap != null) flightMap.onHostPause();
        if (runtime != null) runtime.hold("HOLD – aplikace není v popředí");
        authority.stopToManual();
        updateAuthorityUi();
        super.onPause();
    }

    @Override protected void onStop() {
        invalidatePending(null);
        super.onStop();
    }

    @Override protected void onDestroy() {
        sampling = false;
        main.removeCallbacksAndMessages(null);
        if (flightMap != null) flightMap.onHostDestroy();
        if (tracker != null) tracker.close();
        if (runtime != null) runtime.close();
        if (dji != null) dji.close();
        super.onDestroy();
    }

    private void publishSafetyConfiguration() {
        if (standoffSeek == null || roofHeightSeek == null) return;
        SiteSafetyPlan.Point reference = roofBoundary.isEmpty()
            ? (telemetry.hasAircraftLocation()
                ? new SiteSafetyPlan.Point(telemetry.aircraftLatitude, telemetry.aircraftLongitude) : null)
            : roofBoundary.get(0);
        SiteSafetyPlan plan = new SiteSafetyPlan(roofBoundary, forbiddenZone,
            reference == null ? Double.NaN : reference.latitude,
            reference == null ? Double.NaN : reference.longitude,
            roofHeightSeek.getProgress(), roofSlopeSeek.getProgress(), roofBearingSeek.getProgress());
        SafetyConfiguration configuration = new SafetyConfiguration(standoffSeek.getProgress(),
            standoffCalibrated, plan);
        if (runtime != null) runtime.updateSafetyConfiguration(configuration);
        if (safetyCalibrationText != null) safetyCalibrationText.setText(
            "ODSTUP " + standoffSeek.getProgress() + " m • STŘECHA " + roofBoundary.size()
                + " BODŮ • ZÁKAZ " + forbiddenZone.size() + " BODŮ");
    }

    private FusionStatus currentFusionStatus() {
        TrackingSnapshot tracking = tracker == null ? TrackingSnapshot.empty() : tracker.snapshot();
        return tracking.primary != null ? FusionStatus.IMAGE_OK : FusionStatus.HOLD;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(Runnable changed) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) changed.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private SeekBar safetySeek(int minimum, int maximum, int value, int color, LinearLayout parent,
            String label) {
        TextView text = text(label + " – " + value, 8, MUTED, true);
        parent.addView(text);
        SeekBar seek = new SeekBar(this);
        seek.setMin(minimum); seek.setMax(maximum); seek.setProgress(value);
        seek.setProgressTintList(ColorStateList.valueOf(color));
        seek.setThumbTintList(ColorStateList.valueOf(color));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                text.setText(label + " – " + progress);
                if (fromUser) publishSafetyConfiguration();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek);
        return seek;
    }

    private void showStatus(String message) {
        ui(() -> {
            if (statusText != null) statusText.setText(message);
            if (connectionText != null) connectionText.setText(message);
        });
    }

    private void updateAuthorityUi() {
        ui(() -> {
            ControlAuthority.State current = authority.state();
            StringBuilder chain = new StringBuilder();
            for (ControlAuthority.State state : ControlAuthority.State.values()) {
                if (chain.length() > 0) chain.append("  →  ");
                if (state == current) chain.append('[').append(state.label).append(']');
                else chain.append(state.label);
            }
            if (authorityText != null) {
                authorityText.setText(chain.toString());
                int color = current == ControlAuthority.State.AI_ACTIVE ? GREEN
                    : current == ControlAuthority.State.PILOT_TAKEOVER ? RED
                    : current == ControlAuthority.State.RTH ? ORANGE : CYAN;
                authorityText.setTextColor(color);
                authorityText.setBackground(card(Color.argb(110, 5, 17, 24), 8, color));
            }
            if (rearmAiButton != null) {
                rearmAiButton.setVisibility(authority.manualRearmRequired() ? View.VISIBLE : View.GONE);
            }
            renderCameraDirectorUi();
        });
    }

    private void renderCameraDirectorUi() {
        if (cameraDirectorText == null) return;
        ControlAuthority.State current = authority.state();
        String label;
        int color;
        if (cameraAutomationActive && current == ControlAuthority.State.AI_ACTIVE) {
            label = cameraZoomAvailable
                ? String.format(Locale.getDefault(), "KAMERA AI • AUTO GIMBAL • ZOOM %.2f×",
                    cameraZoomFactor)
                : "KAMERA AI • AUTO GIMBAL • ZOOM NEDOSTUPNÝ";
            color = GREEN;
        } else if (current == ControlAuthority.State.AI_READY) {
            label = "KAMERA AI • PŘIPRAVENA • GIMBAL + ZOOM";
            color = CYAN;
        } else if (current == ControlAuthority.State.PILOT_TAKEOVER) {
            label = "KAMERA AI • VYPNUTA • OVLÁDÁ PILOT";
            color = RED;
        } else if (current == ControlAuthority.State.RTH) {
            label = "KAMERA AI • VYPNUTA • RTH ŘÍDÍ DJI";
            color = ORANGE;
        } else if (current == ControlAuthority.State.AI_ACTIVE) {
            label = "KAMERA AI • SPOUŠTÍ SE";
            color = CYAN;
        } else {
            label = "KAMERA AI • VYPNUTA";
            color = MUTED;
        }
        cameraDirectorText.setText(label);
        cameraDirectorText.setTextColor(color);
        cameraDirectorText.setBackground(card(Color.argb(105, 5, 17, 24), 8, color));
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
        int alpha = color == RED ? 175 : color == ORANGE ? 135 : 95;
        int translucent = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        button.setBackgroundTintList(null);
        button.setBackground(card(translucent, 8, color));
        button.setTextColor(Color.WHITE);
        button.setElevation(0f);
        button.setAlpha(button.isEnabled() ? 1f : 0.45f);
    }

    private LinearLayout.LayoutParams compactButtonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(36));
        params.setMargins(dp(4), 0, 0, 0);
        return params;
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
