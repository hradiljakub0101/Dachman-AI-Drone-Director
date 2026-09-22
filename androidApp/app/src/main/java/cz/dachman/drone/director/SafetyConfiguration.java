package cz.dachman.drone.director;

/** Pilot-reviewed site envelope used by every autonomous command. */
public final class SafetyConfiguration {
    public final double minimumWorkerStandoffMeters;
    public final boolean standoffCalibrated;
    public final SiteSafetyPlan site;
    /** Pilot explicitly accepted that autonomous motion has no verified DJI Home/RTH fallback. */
    public final boolean flightWithoutHomeAccepted;

    public SafetyConfiguration(double minimumWorkerStandoffMeters, boolean calibrated, SiteSafetyPlan site) {
        this(minimumWorkerStandoffMeters, calibrated, site, false);
    }

    public SafetyConfiguration(double minimumWorkerStandoffMeters, boolean calibrated,
            SiteSafetyPlan site, boolean flightWithoutHomeAccepted) {
        this.minimumWorkerStandoffMeters = Math.max(6d, Math.min(30d, minimumWorkerStandoffMeters));
        this.standoffCalibrated = calibrated;
        this.site = site == null ? SiteSafetyPlan.empty() : site;
        this.flightWithoutHomeAccepted = flightWithoutHomeAccepted;
    }
    public static SafetyConfiguration defaults() {
        return new SafetyConfiguration(6d, false, SiteSafetyPlan.empty(), false);
    }
}
