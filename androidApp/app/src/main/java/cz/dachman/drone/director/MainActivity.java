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
    private static final int LANDSCAPE_SIDEBAR_DP = 330;

    private enum PendingKind { NONE, MODE, TAKEOFF, LANDING, RETURN_HOME, FULL_MISSION }

    private final ApprovalGate gate = new ApprovalGate();
    private final ControlAuthority authority = new ControlAuthority();
    private final Handler main = new Handler(Looper.getMainLooper());
    private CancellationSignal authentication;
    private DjiConnection dji;
    private WorkerTracker tracker;
    private FlightRuntime runtime;
    private TextureView video;
    private TrackingOverlay overlay;
    private FlightPathView flightPath;
    private FlightMapView flightMap;
    private FlightRadarView flightRadar;
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
    private TextView authorityText;
    private LinearLayout approvalCard;
    private CheckBox readinessCheck;
    private Button approveButton;
    private Button workerOneButton;
    private Button workerTwoButton;
    private Button recordButton;
    private Button cancelActionButton;
    private Button rearmAiButton;
    private Spinner profileSpinner;
    private SeekBar heightSeek;
    private PendingKind pendingKind = PendingKind.NONE;
    private FlightPlan pendingPlan;
    private TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();
    private boolean videoActive;
    private boolean sampling;
    private boolean aircraftActionActive;
    /** Guided mission: every composition still requires biometric approval and can be aborted by RC. */
    private boolean missionActive;
    private int missionIndex;
    private final FlightMode[] missionModes = new FlightMode[] {
        FlightMode.FOLLOW, FlightMode.ORBIT_RIGHT, FlightMode.PULL_AWAY, FlightMode.REVEAL_UP
    };
    private final Runnable missionPrompt = () -> {
        if (!missionActive || tracker == null) return;
        if (!tracker.snapshot().hasRequiredTargets(missionModes[missionIndex])) {
            missionActive = false;
            showStatus("Mise zastavena – Worker už není bezpečně potvrzen v obrazu.");
            return;
        }
        showStatus("AI navrhuje kompozici " + (missionIndex + 1) + " z " + missionModes.length + ": "
            + missionModes[missionIndex].label + ". Zkontroluj náhled a potvrď.");
        requestMode(missionModes[missionIndex]);
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
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        buildInterface();
        dji = new DjiConnection(this);
        runtime = new FlightRuntime(dji, this);
        tracker = new WorkerTracker(this, this);
        dji.setListener(this);
        if (video.isAvailable()) dji.attachVideo(video.getSurfaceTexture(), video.getWidth(), video.getHeight());
        if (!handleUsbIntent(getIntent())) dji.connect();
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
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        cameraAreaParams.setMargins(0, 0, dp(LANDSCAPE_SIDEBAR_DP), 0);
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
        videoPlaceholder.setBackgroundColor(BACKGROUND);
        videoFrame.addView(videoPlaceholder, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new TrackingOverlay(this);
        overlay.setTapListener(this::selectTarget);
        videoFrame.addView(overlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topHud = row();
        topHud.setPadding(dp(9), dp(5), dp(9), dp(5));
        topHud.setBackground(card(Color.argb(215, 5, 17, 24), 0, PANEL_LIGHT));
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
        Button photo = button("FOTO", PANEL_LIGHT, () -> dji.takePhoto(this::showCompletion));
        topHud.addView(photo, compactButtonParams(dp(54)));

        liveBadge = text("OFF", 9, Color.WHITE, true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setBackground(card(RED, 10, RED));
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(dp(44), dp(34));
        liveParams.setMargins(dp(4), 0, 0, 0);
        topHud.addView(liveBadge, liveParams);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP);
        cameraArea.addView(topHud, topParams);

        telemetryText = text("SDK —  DRON —  BATERIE —  GNSS —  VÝŠKA —", 10, Color.WHITE, true);
        telemetryText.setGravity(Gravity.CENTER_VERTICAL);
        telemetryText.setPadding(dp(9), dp(3), dp(9), dp(3));
        telemetryText.setMaxLines(2);
        telemetryText.setBackground(card(Color.argb(210, 5, 17, 24), 9, GREEN));
        FrameLayout.LayoutParams telemetryParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP);
        telemetryParams.setMargins(dp(8), dp(62), dp(8), 0);
        cameraArea.addView(telemetryText, telemetryParams);

        authorityText = text("[MANUAL] → AI PŘIPRAVENA → AI AKTIVNÍ → PILOT PŘEVZAL → RTH",
            9, Color.WHITE, true);
        authorityText.setGravity(Gravity.CENTER);
        authorityText.setMaxLines(2);
        authorityText.setBackground(card(Color.argb(225, 5, 17, 24), 8, CYAN));
        FrameLayout.LayoutParams authorityParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.TOP);
        authorityParams.setMargins(dp(8), dp(114), dp(8), 0);
        cameraArea.addView(authorityText, authorityParams);

        flightRadar = new FlightRadarView(this);
        FrameLayout.LayoutParams radarParams = new FrameLayout.LayoutParams(
            dp(92), dp(100), Gravity.TOP | Gravity.RIGHT);
        radarParams.setMargins(0, dp(158), dp(9), 0);
        cameraArea.addView(flightRadar, radarParams);

        flightMap = new FlightMapView(this);
        FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(
            dp(142), dp(112), Gravity.BOTTOM | Gravity.LEFT);
        mapParams.setMargins(dp(9), 0, 0, dp(66));
        cameraArea.addView(flightMap, mapParams);

        flightPath = new FlightPathView(this);
        flightPath.setAlpha(0.94f);
        flightPath.setBackground(card(Color.argb(225, 5, 17, 24), 10, CYAN));
        FrameLayout.LayoutParams pathParams = new FrameLayout.LayoutParams(
            dp(174), dp(112), Gravity.BOTTOM | Gravity.RIGHT);
        pathParams.setMargins(0, 0, dp(9), dp(66));
        cameraArea.addView(flightPath, pathParams);

        ScrollView controlScroll = new ScrollView(this);
        controlScroll.setFillViewport(true);
        controlScroll.setVerticalScrollBarEnabled(false);
        LinearLayout controls = column();
        controls.setPadding(dp(10), dp(7), dp(10), dp(14));
        controls.setBackground(card(Color.argb(235, 5, 17, 24), 12, PANEL_LIGHT));
        controlScroll.addView(controls);
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
            dp(LANDSCAPE_SIDEBAR_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        controlParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        root.addView(controlScroll, controlParams);

        LinearLayout statePanel = column();
        statePanel.setPadding(dp(8), dp(5), dp(8), dp(5));
        statePanel.setBackground(card(Color.argb(210, 17, 48, 61), 9, CYAN));
        statusText = text("PREFLIGHT – čekám na připojení", 12, Color.WHITE, true);
        commandText = text("PITCH 0,00  ROLL 0,00  YAW 0,0  VERT 0,00", 9, MUTED, false);
        connectionText = text("DJI SDK se připravuje…", 9, MUTED, false);
        aircraftActionText = text("AUTOMATICKÁ AKCE: ŽÁDNÁ", 9, ORANGE, true);
        statePanel.addView(statusText);
        statePanel.addView(commandText);
        statePanel.addView(connectionText);
        statePanel.addView(aircraftActionText);
        controls.addView(statePanel);

        section(controls, "CÍL AI");
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
            missionActive = false;
            main.removeCallbacks(missionPrompt);
            if (runtime != null) runtime.hold("Cíl zrušen – HOLD");
            authority.stopToManual();
            updateAuthorityUi();
            invalidatePending("Výběr cílů zrušen – HOLD");
        });
        controls.addView(clearWorkers, fullButtonParams());
        selectWorkerSlot(1);

        section(controls, "VŠECHNY REŽIMY LETU");
        GridLayout modeGrid = grid();
        modeGrid.setColumnCount(4);
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

        approvalCard = column();
        approvalCard.setPadding(dp(9), dp(8), dp(9), dp(8));
        approvalCard.setBackground(card(Color.rgb(23, 57, 67), 10, CYAN));
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
        addGridButton(aircraftGrid, "PŘISTÁNÍ", ORANGE, () -> requestAircraftAction(PendingKind.LANDING));
        addGridButton(aircraftGrid, "NÁVRAT DOMŮ", ORANGE, () -> requestAircraftAction(PendingKind.RETURN_HOME));
        addGridButton(aircraftGrid, "AUTONOMNÍ MISE", CYAN, this::requestFullMission);
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
        TextView safety = text("Kniply RC-N1 mají vždy přednost. Pilot odpovídá za volnou trasu.", 9, MUTED, false);
        safety.setPadding(0, dp(5), 0, 0);
        controls.addView(safety);

        LinearLayout quickBar = row();
        quickBar.setPadding(dp(6), dp(4), dp(6), dp(5));
        quickBar.setBackground(card(Color.argb(245, 5, 17, 24), 0, GREEN));
        quickBar.addView(button("FOLLOW", PANEL_LIGHT, () -> requestMode(FlightMode.FOLLOW)), weightedButtonParams());
        quickBar.addView(button("ORBIT", PANEL_LIGHT, () -> requestMode(FlightMode.ORBIT_RIGHT)), weightedButtonParams());
        quickBar.addView(button("ROPE", PANEL_LIGHT, () -> requestMode(FlightMode.ROPE_MODE)), weightedButtonParams());
        quickBar.addView(button("PULL", PANEL_LIGHT, () -> requestMode(FlightMode.PULL_AWAY)), weightedButtonParams());
        quickBar.addView(button("ABORT", RED, this::abort), weightedButtonParams());
        cameraArea.addView(quickBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(61), Gravity.BOTTOM));

        setContentView(root);
        updateAuthorityUi();
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
        if (authority.manualRearmRequired()) {
            showStatus("Pilot převzal řízení. Nejdřív stiskni RUČNĚ PŘIPRAVIT AI ZNOVU.");
            return;
        }
        if (!snapshot.hasRequiredTargets(mode)) {
            showStatus(mode.requiresSecondary
                ? "Nejprve označ Worker 1 i Worker 2 v živém obrazu."
                : "Nejprve označ Worker 1 v živém obrazu.");
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

    private void requestFullMission() {
        if (authority.manualRearmRequired()) {
            showStatus("Pilot převzal řízení. Nejdřív stiskni RUČNĚ PŘIPRAVIT AI ZNOVU.");
            return;
        }
        if (tracker == null || !tracker.snapshot().hasRequiredTargets(FlightMode.FOLLOW)) {
            showStatus("Nejprve označ a potvrď Worker 1 v živém obrazu.");
            return;
        }
        authority.targetConfirmed(true);
        updateAuthorityUi();
        if (runtime != null && runtime.isActive()) runtime.abort("Příprava mise – HOLD");
        invalidatePending(null);
        pendingKind = PendingKind.FULL_MISSION;
        gate.request("AUTONOMNÍ MISE: vzlet, potvrzené sledování Worker 1, čtyři kompozice a návrat domů");
        pendingText.setText("AUTONOMNÍ MISE\nVzlet → sledování Worker 1 → čtyři schválené kompozice → RTH.");
        readinessCheck.setVisibility(View.VISIBLE);
        readinessCheck.setChecked(false);
        approvalCard.setVisibility(View.VISIBLE);
        flightPath.setPlan(new FlightPlan(FlightMode.FOLLOW,
            FlightLevel.ofMeters(heightSeek.getProgress()), (FlightProfile)profileSpinner.getSelectedItem()));
        updateApprovalButton();
        showStatus("Před startem potvrď volný prostor, baterii, GPS a přímý dohled.");
    }

    private void confirmAiRearm() {
        boolean targetAvailable = tracker != null
            && tracker.snapshot().hasRequiredTargets(FlightMode.FOLLOW);
        if (!authority.confirmManualRearm(targetAvailable)) {
            showStatus("AI nelze připravit. Znovu označ Worker 1 a ověř živý obraz.");
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
                        showStatus("Kompozice dokončeny. Zkontroluj trasu a potvrď bezpečný návrat domů.");
                        requestAircraftAction(PendingKind.RETURN_HOME);
                    }
                }, 8_000L);
            });
        } else if (kind == PendingKind.TAKEOFF) {
            runtime.abort("Autonomní vzlet – Virtual Stick vypnut");
            dji.startTakeoff(this::showCompletion);
        } else if (kind == PendingKind.LANDING) {
            runtime.abort("Autonomní přistání – Virtual Stick vypnut");
            dji.startLanding(this::showCompletion);
        } else if (kind == PendingKind.RETURN_HOME) {
            runtime.abort("Návrat domů – Virtual Stick vypnut");
            authority.beginRth(true);
            updateAuthorityUi();
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
            || pendingKind == PendingKind.LANDING || pendingKind == PendingKind.RETURN_HOME
            || pendingKind == PendingKind.FULL_MISSION;
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
            missionActive = false;
            main.removeCallbacks(missionPrompt);
            invalidatePending(null);
            authority.pilotTookOver();
            updateAuthorityUi();
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
                authorityText.setBackground(card(Color.argb(225, 5, 17, 24), 8, color));
            }
            if (rearmAiButton != null) {
                rearmAiButton.setVisibility(authority.manualRearmRequired() ? View.VISIBLE : View.GONE);
            }
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
