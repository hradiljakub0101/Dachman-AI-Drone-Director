package cz.dachman.drone.director;

public final class FlightPlan {
    public final FlightMode mode;
    public final FlightLevel level;
    public final FlightProfile profile;

    public FlightPlan(FlightMode mode, FlightLevel level, FlightProfile profile) {
        this.mode = mode;
        this.level = level;
        this.profile = profile;
    }

    public String approvalText() {
        return mode.label + " / " + profile.label + " / " + level.label;
    }
}
