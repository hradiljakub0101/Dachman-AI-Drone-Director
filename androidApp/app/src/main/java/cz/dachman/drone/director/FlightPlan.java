package cz.dachman.drone.director;

public final class FlightPlan {
    public final FlightMode mode;
    public final FlightLevel level;
    public final FlightProfile profile;
    public final TargetSelection targets;

    public FlightPlan(FlightMode mode, FlightLevel level, FlightProfile profile) {
        this(mode, level, profile, mode.requiresSecondary ? TargetSelection.BOTH : TargetSelection.WORKER_ONE);
    }

    public FlightPlan(FlightMode mode, FlightLevel level, FlightProfile profile, TargetSelection targets) {
        this.mode = mode;
        this.level = level;
        this.profile = profile;
        this.targets = targets == null ? TargetSelection.WORKER_ONE : targets;
    }

    public String approvalText() {
        return mode.label + " / " + targets.label + " / " + profile.label + " / " + level.label;
    }
}
