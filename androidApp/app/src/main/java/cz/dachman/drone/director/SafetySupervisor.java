package cz.dachman.drone.director;

/** Deterministic guard between vision/planning and DJI virtual-stick commands. */
public final class SafetySupervisor {
    private static final boolean VISUAL_TARGET_GATES_FLIGHT = true;
    public static final int MINIMUM_BATTERY_PERCENT = 25;
    public static final int MINIMUM_SATELLITES = 8;
    public static final int MINIMUM_SIGNAL_PERCENT = 20;
    public static final long TARGET_HOLD_AFTER_MILLIS = 900L;
    public static final long TARGET_STOP_AFTER_MILLIS = 2_200L;
    public static final double MINIMUM_WORKER_STANDOFF_METERS = 6d;
    public static final double MAXIMUM_WORKER_DISTANCE_METERS = 50d;

    public SafetyDecision evaluate(FlightPlan plan, TelemetrySnapshot telemetry,
            TrackingSnapshot tracking, long nowMillis, long startedAtMillis) {
        return evaluate(plan, telemetry, tracking, SafetyConfiguration.defaults(), nowMillis, startedAtMillis);
    }

    public SafetyDecision evaluate(FlightPlan plan, TelemetrySnapshot telemetry,
            TrackingSnapshot tracking, SafetyConfiguration configuration,
            long nowMillis, long startedAtMillis) {
        if (plan == null || plan.mode == FlightMode.HOLD) return SafetyDecision.stop("Nebyl zvolen letový režim.");
        if (!telemetry.sdkRegistered) return SafetyDecision.stop("DJI SDK není zaregistrováno.");
        if (!telemetry.connected) return SafetyDecision.stop("Dron nebo ovladač není připojen.");
        if (!telemetry.motorsOn || !telemetry.flying) return SafetyDecision.stop("Let musí zahájit pilot ručně.");
        if (telemetry.failsafe) return SafetyDecision.stop("DJI failsafe – řízení převzal letový kontrolér.");
        if (telemetry.goingHome) return SafetyDecision.stop("Probíhá návrat domů – aplikace uvolnila řízení.");
        if (telemetry.batteryPercent < 0) return SafetyDecision.hold("Čekám na stav baterie.");
        if (telemetry.batteryPercent < MINIMUM_BATTERY_PERCENT) return SafetyDecision.stop("Baterie je pod bezpečnostním limitem.");
        // DJI's controller is authoritative for GNSS/Home Point validity. Satellite count
        // remains visible to the pilot, but is not a second, contradictory hard threshold.
        if (!telemetry.hasHomeLocation()) return SafetyDecision.hold("Čekám na Home Point potvrzený letovým kontrolérem DJI.");
        if (telemetry.signalPercent < 0) return SafetyDecision.hold("Čekám na kvalitu rádiového spojení.");
        if (telemetry.signalPercent < MINIMUM_SIGNAL_PERCENT) return SafetyDecision.stop("Slabé spojení mezi ovladačem a dronem.");
        if ("LEVEL_2".equals(telemetry.windLevel)) return SafetyDecision.stop("Silný vítr – automatický režim zastaven.");
        if (telemetry.altitudeMeters < 1.5f) return SafetyDecision.hold("Nejprve ručně vystoupej do bezpečné výšky.");
        if (telemetry.altitudeMeters > plan.level.maximumAltitudeMeters + 0.5f) {
            return SafetyDecision.stop("Překročen zvolený výškový limit.");
        }
        if (plan.mode.maxDurationMillis > 0 && nowMillis - startedAtMillis > plan.mode.maxDurationMillis) {
            return SafetyDecision.stop("Časový limit manévru vypršel; je nutné nové schválení.");
        }
        if (VISUAL_TARGET_GATES_FLIGHT && plan.mode.requiresVisualTarget()) {
            if (!tracking.hasRequiredTargets(plan.mode)) return SafetyDecision.hold("Chybí potvrzený pracovník pro tento režim.");
            long oldest = tracking.primary == null ? nowMillis : tracking.primary.observedAtMillis;
            float confidence = tracking.primary == null ? 0f : tracking.primary.confidence;
            if (plan.mode.requiresSecondary) {
                oldest = Math.min(oldest, tracking.secondary.observedAtMillis);
                confidence = Math.min(confidence, tracking.secondary.confidence);
            }
            long age = nowMillis - oldest;
            if (age > TARGET_STOP_AFTER_MILLIS) return SafetyDecision.stop("AI ztratila sledovaný cíl.");
            if (age > TARGET_HOLD_AFTER_MILLIS) return SafetyDecision.hold("AI dočasně nevidí cíl – HOLD.");
            if (confidence < 0.35f) return SafetyDecision.hold("Nízká jistota rozpoznání pracovníka.");
        }
        SiteSafetyPlan site = configuration == null ? SiteSafetyPlan.empty() : configuration.site;
        if (telemetry.hasAircraftLocation() && (site.hasRoofBoundary() || site.hasForbiddenZone())) {
            if (!site.aircraftAllowed(telemetry.aircraftLatitude, telemetry.aircraftLongitude)) {
                return SafetyDecision.stop("Dron opustil hranici střechy nebo vstoupil do zakázané zóny.");
            }
            float roofHeight = site.roofHeightAt(telemetry.aircraftLatitude, telemetry.aircraftLongitude);
            if (Float.isFinite(roofHeight) && telemetry.altitudeMeters - roofHeight < 3f) {
                return SafetyDecision.stop("Nedostatečná výška nad vypočtenou rovinou střechy.");
            }
        }
        return SafetyDecision.allow();
    }
}
