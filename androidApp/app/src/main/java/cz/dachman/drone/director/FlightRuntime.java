package cz.dachman.drone.director;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends commands at ten hertz and applies the safety supervisor before every packet. */
public final class FlightRuntime {
    public interface Listener {
        void onRuntimeState(boolean active, String message, FlightCommand command);
    }

    private final DroneSession session;
    private final Listener listener;
    private final SafetySupervisor safety = new SafetySupervisor();
    private final FlightDirector director = new FlightDirector();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();
    private volatile TrackingSnapshot tracking = TrackingSnapshot.empty();
    private volatile WorkerGeoSnapshot workerGeo = WorkerGeoSnapshot.empty();
    private volatile boolean active;
    private volatile FlightPlan plan;
    private volatile long startedAt;
    private volatile long holdSince;
    private volatile long revision;
    private volatile boolean closed;
    private String lastMessage = "";

    public FlightRuntime(DroneSession session, Listener listener) {
        this.session = session;
        this.listener = listener;
        timer.scheduleAtFixedRate(this::tick, 100L, 100L, TimeUnit.MILLISECONDS);
    }

    public void updateTelemetry(TelemetrySnapshot telemetry) { this.telemetry = telemetry; }
    public void updateTracking(TrackingSnapshot tracking) { this.tracking = tracking; }
    public void updateWorkerGeo(WorkerGeoSnapshot workerGeo) {
        this.workerGeo = workerGeo == null ? WorkerGeoSnapshot.empty() : workerGeo;
    }
    public void updateAppliedZoom(boolean supported, float factor) {
        director.updateAppliedZoom(supported, factor);
    }
    public boolean isActive() { return active; }

    public SafetyDecision preflight(FlightPlan candidate) {
        return safety.evaluate(candidate, telemetry, tracking, workerGeo, now(), now());
    }

    public synchronized void start(FlightPlan candidate, DroneSession.Completion completion) {
        if (closed) { completion.onComplete(false, "Řízení aplikace je ukončeno."); return; }
        if (active) { completion.onComplete(false, "Jiný manévr je právě aktivní."); return; }
        SafetyDecision decision = preflight(candidate);
        if (decision.action != SafetyDecision.Action.ALLOW) {
            completion.onComplete(false, decision.reason);
            return;
        }
        final long challenge = ++revision;
        director.begin(candidate, telemetry, tracking, workerGeo);
        session.enableVirtualStick((success, message) -> {
            if (challenge != revision || closed) {
                if (success) session.disableVirtualStick("Pozdní spuštění bylo zrušeno.", (ignored, detail) -> {});
                completion.onComplete(false, "Spuštění manévru bylo zrušeno.");
                return;
            }
            if (!success) { completion.onComplete(false, message); return; }
            plan = candidate;
            startedAt = now();
            holdSince = 0L;
            active = true;
            emit("AKTIVNÍ: " + candidate.mode.label, FlightCommand.ZERO);
            completion.onComplete(true, message);
        });
    }

    public void hold(String reason) { stop(reason == null ? "HOLD" : reason); }
    public void abort(String reason) { stop(reason == null ? "ABORT" : reason); }

    private void tick() {
        FlightPlan current = plan;
        if (!active || current == null) return;
        long now = now();
        SafetyDecision decision = safety.evaluate(current, telemetry, tracking, workerGeo, now, startedAt);
        if (decision.action == SafetyDecision.Action.STOP) {
            stop(decision.reason);
            return;
        }
        if (decision.action == SafetyDecision.Action.HOLD) {
            session.sendCommand(FlightCommand.ZERO);
            emit(decision.reason, FlightCommand.ZERO);
            if (holdSince == 0L) holdSince = now;
            if (now - holdSince > 2_500L) stop(decision.reason);
            return;
        }
        holdSince = 0L;
        FlightCommand command = director.command(current, telemetry, tracking, workerGeo);
        session.sendCommand(command);
        emit("AKTIVNÍ: " + current.mode.label, command);
    }

    private synchronized void stop(String reason) {
        revision++;
        if (!active && plan == null) return;
        active = false;
        plan = null;
        holdSince = 0L;
        director.reset();
        session.sendCommand(FlightCommand.ZERO);
        session.disableVirtualStick(reason, (success, message) -> emit(reason, FlightCommand.ZERO));
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
        timer.shutdownNow();
    }

    private static long now() { return android.os.SystemClock.elapsedRealtime(); }
}
