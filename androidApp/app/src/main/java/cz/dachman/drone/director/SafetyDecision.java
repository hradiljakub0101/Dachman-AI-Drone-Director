package cz.dachman.drone.director;

public final class SafetyDecision {
    public enum Action { ALLOW, HOLD, STOP }
    public final Action action;
    public final String reason;

    private SafetyDecision(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    public static SafetyDecision allow() { return new SafetyDecision(Action.ALLOW, "Řízení povoleno"); }
    public static SafetyDecision hold(String reason) { return new SafetyDecision(Action.HOLD, reason); }
    public static SafetyDecision stop(String reason) { return new SafetyDecision(Action.STOP, reason); }
}
