package cz.dachman.drone.director;

/**
 * Safety latch deciding whether the pilot or the application owns motion control.
 * A physical RC takeover is sticky: target detection alone can never re-enable AI.
 */
public final class ControlAuthority {
    public enum State {
        MANUAL("MANUAL"),
        AI_READY("AI PŘIPRAVENA"),
        AI_ACTIVE("AI AKTIVNÍ"),
        PILOT_TAKEOVER("PILOT PŘEVZAL"),
        RTH("RTH");

        public final String label;
        State(String label) { this.label = label; }
    }

    private State state = State.MANUAL;
    private boolean manualRearmRequired;

    public synchronized State state() { return state; }
    public synchronized boolean manualRearmRequired() { return manualRearmRequired; }

    /** A newly confirmed target may prepare AI unless the pilot takeover latch is set. */
    public synchronized boolean targetConfirmed(boolean targetAvailable) {
        if (!targetAvailable || manualRearmRequired || state == State.RTH) return false;
        state = State.AI_READY;
        return true;
    }

    /** Explicit APK action required after physical RC takeover. */
    public synchronized boolean confirmManualRearm(boolean targetAvailable) {
        if (!targetAvailable || state == State.RTH) return false;
        manualRearmRequired = false;
        state = State.AI_READY;
        return true;
    }

    public synchronized boolean activateAi() {
        if (manualRearmRequired || state != State.AI_READY) return false;
        state = State.AI_ACTIVE;
        return true;
    }

    public synchronized void compositionCompleted() {
        if (!manualRearmRequired && state != State.RTH) state = State.AI_READY;
    }

    public synchronized void stopToManual() {
        if (!manualRearmRequired && state != State.RTH) state = State.MANUAL;
    }

    public synchronized void pilotTookOver() {
        manualRearmRequired = true;
        state = State.PILOT_TAKEOVER;
    }

    public synchronized boolean beginRth(boolean humanApproved) {
        if (!humanApproved) return false;
        state = State.RTH;
        return true;
    }

    public synchronized void finishRth() {
        manualRearmRequired = false;
        state = State.MANUAL;
    }
}
