package cz.dachman.drone.director;

/** Immutable binding between visual Worker slots and authenticated positioning tags. */
public final class WorkerGeoSnapshot {
    public final String primaryId;
    public final String secondaryId;
    public final WorkerPositionFix primary;
    public final WorkerPositionFix secondary;

    public WorkerGeoSnapshot(String primaryId, String secondaryId, WorkerPositionFix primary,
            WorkerPositionFix secondary) {
        this.primaryId = primaryId == null ? "" : primaryId;
        this.secondaryId = secondaryId == null ? "" : secondaryId;
        this.primary = primary;
        this.secondary = secondary;
    }

    public static WorkerGeoSnapshot empty() { return new WorkerGeoSnapshot("", "", null, null); }
    public boolean hasAnyBinding() { return !primaryId.isEmpty() || !secondaryId.isEmpty(); }
    public boolean requiredBound(FlightMode mode) {
        return (!mode.requiresPrimary || !primaryId.isEmpty())
            && (!mode.requiresSecondary || !secondaryId.isEmpty());
    }
    public boolean requiredReliable(FlightMode mode, long now) {
        return (!mode.requiresPrimary || reliable(primary, now))
            && (!mode.requiresSecondary || reliable(secondary, now));
    }
    public WorkerPositionFix targetFor(FlightMode mode) {
        if (!mode.requiresSecondary || secondary == null) return primary;
        if (primary == null) return secondary;
        return new WorkerPositionFix(primaryId + "+" + secondaryId,
            (primary.latitude + secondary.latitude) * 0.5d,
            (primary.longitude + secondary.longitude) * 0.5d,
            Math.max(primary.accuracyMeters, secondary.accuracyMeters),
            Math.min(primary.observedAtMillis, secondary.observedAtMillis),
            primary.source == WorkerPositionFix.Source.UWB_FUSED
                && secondary.source == WorkerPositionFix.Source.UWB_FUSED
                    ? WorkerPositionFix.Source.UWB_FUSED : WorkerPositionFix.Source.PHONE_GNSS,
            (primary.northMetersPerSecond + secondary.northMetersPerSecond) * 0.5f,
            (primary.eastMetersPerSecond + secondary.eastMetersPerSecond) * 0.5f);
    }
    private static boolean reliable(WorkerPositionFix fix, long now) {
        return fix != null && fix.isStructurallyValid() && fix.isFresh(now) && fix.isAccurate();
    }
}
