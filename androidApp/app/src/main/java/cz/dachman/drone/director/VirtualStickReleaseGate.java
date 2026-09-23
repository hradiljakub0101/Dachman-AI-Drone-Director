package cz.dachman.drone.director;

import java.util.ArrayList;
import java.util.List;

/** Coalesces asynchronous SDK shutdown requests so aircraft actions wait for the same result. */
final class VirtualStickReleaseGate {
    interface Release { void start(DroneSession.Completion completion); }

    private boolean pending;
    private long generation;
    private final List<DroneSession.Completion> waiters = new ArrayList<>();

    synchronized boolean pending() { return pending; }

    void request(Release release, DroneSession.Completion completion) {
        final long current;
        synchronized (this) {
            waiters.add(completion);
            if (pending) return;
            pending = true;
            current = ++generation;
        }
        try {
            release.start((success, message) -> finish(current, success, message));
        } catch (RuntimeException error) {
            finish(current, false, "DJI přerušilo vypínání Virtual Stick: "
                + error.getClass().getSimpleName());
        }
    }

    void cancel(String reason) {
        List<DroneSession.Completion> ready;
        synchronized (this) {
            generation++;
            pending = false;
            ready = new ArrayList<>(waiters);
            waiters.clear();
        }
        for (DroneSession.Completion waiter : ready) waiter.onComplete(false, reason);
    }

    private void finish(long current, boolean success, String message) {
        List<DroneSession.Completion> ready;
        synchronized (this) {
            if (current != generation || !pending) return;
            pending = false;
            ready = new ArrayList<>(waiters);
            waiters.clear();
        }
        for (DroneSession.Completion waiter : ready) waiter.onComplete(success, message);
    }
}
