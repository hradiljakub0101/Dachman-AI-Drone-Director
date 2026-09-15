package cz.dachman.drone.director;

/** Pilot-reviewed site envelope used by every autonomous command. */
public final class SafetyConfiguration {
    public final double minimumWorkerStandoffMeters;
    public final boolean standoffCalibrated;
    public final SiteSafetyPlan site;

    public SafetyConfiguration(double minimumWorkerStandoffMeters, boolean calibrated, SiteSafetyPlan site) {
        this.minimumWorkerStandoffMeters = Math.max(6d, Math.min(30d, minimumWorkerStandoffMeters));
        this.standoffCalibrated = calibrated;
        this.site = site == null ? SiteSafetyPlan.empty() : site;
    }
    public static SafetyConfiguration defaults() { return new SafetyConfiguration(6d, false, SiteSafetyPlan.empty()); }
}
