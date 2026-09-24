package cz.dachman.drone.director;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Sends commands at ten hertz and applies the safety supervisor before every packet. */
public final class FlightRuntime {
    public interface Listener {
        void onRuntimeState(boolean active, String message, FlightCommand command);
        default void onSpatialModelUpdated(BuildingSpatialModel model) {}
    }

    private final DroneSession session;
    private final Listener listener;
    private final SafetySupervisor safety = new SafetySupervisor();
    private final FlightDirector director = new FlightDirector();
    private final ScheduledExecutorService timer;
    private final LongSupplier clock;
    private volatile TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();
    private volatile TrackingSnapshot tracking = TrackingSnapshot.empty();
    private volatile SafetyConfiguration safetyConfiguration = SafetyConfiguration.defaults();
    private volatile boolean active;
    private boolean starting;
    private long lastTelemetryAt;
    private long lastAcceptedAt;
    private volatile FlightPlan plan;
    private volatile long startedAt;
    private volatile long holdSince;
    private volatile long revision;
    private volatile boolean closed;
    private volatile FlightCommand lastCommand = FlightCommand.ZERO;
    private volatile BuildingSpatialModel spatialModel;
    private volatile ReturnHomeStatus verifiedHome = ReturnHomeStatus.missing("Home není ověřen.");
    private volatile ReturnHomeStatus pinnedApproachHome;
    private double approachStartDistance = Double.NaN;
    private double approachBestDistance = Double.NaN;
    private long approachProgressAt;
    private BuildingSpatialModel.Builder surveyBuilder;
    private String lastMessage = "";

    public FlightRuntime(DroneSession session, Listener listener) {
        this(session, listener, android.os.SystemClock::elapsedRealtime, true);
    }

    /** Same production loop, with a manual clock and no timer for deterministic offline tests. */
    FlightRuntime(DroneSession session, Listener listener, LongSupplier clock, boolean schedule) {
        this.session = session;
        this.listener = listener;
        this.clock = clock;
        timer = schedule ? Executors.newSingleThreadScheduledExecutor() : null;
        if (timer != null) timer.scheduleAtFixedRate(this::tickSafely, 100L, 100L, TimeUnit.MILLISECONDS);
    }

    public synchronized void updateTelemetry(TelemetrySnapshot telemetry) {
        this.telemetry = telemetry;
        lastTelemetryAt = now();
    }
    public void updateTracking(TrackingSnapshot tracking) { this.tracking = tracking; }
    public void updateVerifiedHome(ReturnHomeStatus status) {
        verifiedHome = status == null ? ReturnHomeStatus.missing("Home není ověřen.") : status;
    }
    public void updateSafetyConfiguration(SafetyConfiguration configuration) {
        safetyConfiguration = configuration == null ? SafetyConfiguration.defaults() : configuration;
    }
    public void updateAppliedZoom(boolean supported, float factor) {
        director.updateAppliedZoom(supported, factor);
    }
    public boolean isActive() { return active; }
    public BuildingSpatialModel spatialModel() { return spatialModel; }

    public synchronized SafetyDecision preflight(FlightPlan candidate) {
        if (now() - lastTelemetryAt > 1_500L) return SafetyDecision.stop("Telemetrie dronu není aktuální.");
        SafetyDecision decision = safety.evaluate(candidate, telemetry, tracking, safetyConfiguration, now(), now());
        if (decision.action != SafetyDecision.Action.ALLOW || candidate.mode != FlightMode.HOME_APPROACH)
            return decision;
        HomeApproachGuidance.Result approach = HomeApproachGuidance.calculate(telemetry,
            verifiedHome, safetyConfiguration);
        return approach.stopReason != null || approach.arrived
            ? SafetyDecision.stop(approach.stopReason) : decision;
    }

    public synchronized void start(FlightPlan candidate, DroneSession.Completion completion) {
        if (closed) { completion.onComplete(false, "Řízení aplikace je ukončeno."); return; }
        if (candidate == null || candidate.mode == FlightMode.HOLD) {
            completion.onComplete(false, "Vyber platný autonomní režim."); return;
        }
        if (active || starting) { completion.onComplete(false, "Jiný manévr se spouští nebo je aktivní."); return; }
        SafetyDecision decision = preflight(candidate);
        if (decision.action != SafetyDecision.Action.ALLOW) {
            completion.onComplete(false, decision.reason);
            return;
        }
        final long challenge = ++revision;
        starting = true;
        startedAt = now();
        pinnedApproachHome = candidate.mode == FlightMode.HOME_APPROACH ? verifiedHome : null;
        director.begin(candidate, telemetry, tracking, safetyConfiguration);
        if (candidate.mode == FlightMode.SURVEY_MAP) {
            surveyBuilder = new BuildingSpatialModel.Builder(safetyConfiguration.site.roofBoundary);
        }
        try { session.enableVirtualStick((success, message) -> {
          synchronized (FlightRuntime.this) {
            if (challenge != revision || closed) {
                completion.onComplete(false, "Spuštění manévru bylo zrušeno.");
                return;
            }
            starting = false;
            SafetyDecision refreshed = preflight(candidate);
            if (candidate.mode == FlightMode.HOME_APPROACH && !approachHomeStillVerified()) {
                refreshed = SafetyDecision.stop("Home Point se během přípravy změnil.");
            }
            if (!success || refreshed.action != SafetyDecision.Action.ALLOW) {
                if (success) session.disableVirtualStick(refreshed.reason, (ignored, detail) -> {});
                completion.onComplete(false, success ? refreshed.reason : message);
                return;
            }
            plan = candidate;
            startedAt = now();
            lastAcceptedAt = startedAt;
            holdSince = 0L;
            lastCommand = FlightCommand.ZERO;
            if (candidate.mode == FlightMode.HOME_APPROACH) {
                HomeApproachGuidance.Result initial = HomeApproachGuidance.calculate(telemetry,
                    pinnedApproachHome, safetyConfiguration);
                approachStartDistance = initial.distanceMeters;
                approachBestDistance = initial.distanceMeters;
                approachProgressAt = now();
            }
            active = true;
            emit("AKTIVNÍ: " + candidate.mode.label, FlightCommand.ZERO);
            completion.onComplete(true, message);
          }
        }); }
        catch (RuntimeException error) {
            starting = false;
            completion.onComplete(false, "Zapnutí DJI Virtual Stick selhalo: "
                + error.getClass().getSimpleName());
        }
    }

    public void hold(String reason) { stop(reason == null ? "HOLD" : reason); }
    public void abort(String reason) { stop(reason == null ? "ABORT" : reason); }

    private void tickSafely() {
        try { tick(); }
        catch (RuntimeException error) { stop("Chyba řídicí smyčky – AI vypnuta: " + error.getClass().getSimpleName()); }
    }

    synchronized void tick() {
        if (starting && now() - startedAt > 3_000L) {
            stop("DJI nepotvrdilo zapnutí řízení – AI vypnuta.");
            return;
        }
        FlightPlan current = plan;
        if (!active || current == null) return;
        long now = now();
        if (now - lastTelemetryAt > 1_500L) { stop("Telemetrie dronu není aktuální – AI vypnuta."); return; }
        if (now - lastAcceptedAt > 1_500L) { stop("DJI nepotvrzuje příjem povelů – AI vypnuta."); return; }
        SafetyDecision decision = safety.evaluate(current, telemetry, tracking,
            safetyConfiguration, now, startedAt);
        if (decision.action == SafetyDecision.Action.STOP) {
            stop(decision.reason);
            return;
        }
        if (decision.action == SafetyDecision.Action.HOLD) {
            if (current.mode == FlightMode.HOME_APPROACH) { stop(decision.reason); return; }
            if (holdSince == 0L) holdSince = now;
            boolean localizationLost = !telemetry.hasAircraftLocation()
                && (current.mode.requiresMapRoute() || current.mode.requiresMapOrbitCenter());
            FlightCommand braking = localizationLost
                ? brakingCommand(lastCommand, now - holdSince) : FlightCommand.ZERO;
            send(braking);
            if (!active) return;
            emit(localizationLost ? "AI BRZDÍ – ztráta prostorové lokalizace" : decision.reason, braking);
            if (now - holdSince > 2_500L) stop(decision.reason);
            return;
        }
        holdSince = 0L;
        FlightCommand command;
        if (current.mode == FlightMode.HOME_APPROACH) {
            HomeApproachGuidance.Result approach = HomeApproachGuidance.calculate(telemetry,
                pinnedApproachHome, safetyConfiguration);
            if (!approachHomeStillVerified() || approach.stopReason != null) {
                stop(approach.stopReason == null ? "Home Point se změnil." : approach.stopReason);
                return;
            }
            if (approach.arrived) { stop(approach.stopReason); return; }
            if (approach.distanceMeters > approachStartDistance + 2d) {
                stop("Přiblížení se vzdaluje od Home: pilot přebírá řízení."); return;
            }
            if (approach.distanceMeters < approachBestDistance - 0.8d) {
                approachBestDistance = approach.distanceMeters;
                approachProgressAt = now;
            }
            if (now - approachProgressAt > 10_000L) {
                stop("Přiblížení nemá ověřitelný postup k Home: pilot přebírá řízení."); return;
            }
            command = approach.command;
        } else {
            command = director.command(current, telemetry, tracking, safetyConfiguration);
        }
        if (current.mode == FlightMode.SURVEY_MAP && surveyBuilder != null) {
            surveyBuilder.observe(telemetry);
            BuildingSpatialModel next = surveyBuilder.snapshot(now);
            spatialModel = next;
            if (next.observations % 10 == 0) listener.onSpatialModelUpdated(next);
        }
        send(command);
        if (!active) return;
        lastCommand = command;
        emit("AKTIVNÍ: " + current.mode.label, command);
    }

    private void send(FlightCommand command) {
        VirtualStickPacket.from(command);
        final long generation = revision;
        session.sendCommand(command, (success, message) -> {
            synchronized (FlightRuntime.this) {
                if (!active || generation != revision) return;
                if (success) lastAcceptedAt = now();
                else stop("DJI odmítlo pohyb – AI vypnuta: " + message);
            }
        });
    }

    private synchronized void stop(String reason) {
        revision++;
        if (!active && !starting && plan == null) return;
        starting = false;
        active = false;
        if (plan == null || plan.mode != FlightMode.SURVEY_MAP || surveyBuilder == null) {
            surveyBuilder = null;
        } else {
            spatialModel = surveyBuilder.snapshot(now());
            listener.onSpatialModelUpdated(spatialModel);
            surveyBuilder = null;
        }
        plan = null;
        pinnedApproachHome = null;
        holdSince = 0L;
        director.reset();
        lastCommand = FlightCommand.ZERO;
        session.sendCommand(FlightCommand.ZERO);
        emit(reason, FlightCommand.ZERO);
        final long generation = revision;
        session.disableVirtualStick(reason, (success, message) -> {
            synchronized (FlightRuntime.this) {
                if (!success && generation == revision) emit(message, FlightCommand.ZERO);
            }
        });
    }

    private void emit(String message, FlightCommand command) {
        if (!message.equals(lastMessage) || command != FlightCommand.ZERO) {
            lastMessage = message;
            listener.onRuntimeState(active, message, command);
        }
    }

    public synchronized void close() {
        closed = true;
        revision++;
        stop("Aplikace ukončena – HOLD");
        if (timer != null) timer.shutdownNow();
    }

    private long now() { return clock.getAsLong(); }

    private boolean approachHomeStillVerified() {
        ReturnHomeStatus current = verifiedHome, pinned = pinnedApproachHome;
        return current != null && pinned != null && current.ready && pinned.ready
            && pinned.savedAtEpochMillis == current.savedAtEpochMillis
            && pinned.rthHeightMeters == current.rthHeightMeters
            && ReturnHomeStatus.distanceMeters(pinned.latitude, pinned.longitude,
                current.latitude, current.longitude) < 0.1d;
    }

    static FlightCommand brakingCommand(FlightCommand previous, long elapsedMillis) {
        if (previous == null || elapsedMillis >= 1_500L) return FlightCommand.ZERO;
        float factor = Math.max(0f, 1f - elapsedMillis / 1_500f);
        return new FlightCommand(previous.pitch * factor, previous.roll * factor,
            previous.yaw * factor, previous.vertical * factor, previous.gimbalPitch,
            previous.digitalZoomFactor);
    }
}
