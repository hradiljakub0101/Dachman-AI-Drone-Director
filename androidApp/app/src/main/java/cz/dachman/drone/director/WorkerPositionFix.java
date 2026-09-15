package cz.dachman.drone.director;

/** Authenticated geographic observation supplied by a worker phone or UWB/GNSS gateway. */
public final class WorkerPositionFix {
    public enum Source { PHONE_GNSS, UWB_FUSED }

    public final String workerId;
    public final double latitude;
    public final double longitude;
    public final float accuracyMeters;
    public final long observedAtMillis;
    public final Source source;
    public final float northMetersPerSecond;
    public final float eastMetersPerSecond;

    public WorkerPositionFix(String workerId, double latitude, double longitude, float accuracyMeters,
            long observedAtMillis, Source source, float northMetersPerSecond, float eastMetersPerSecond) {
        this.workerId = workerId == null ? "" : workerId.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.observedAtMillis = observedAtMillis;
        this.source = source == null ? Source.PHONE_GNSS : source;
        this.northMetersPerSecond = northMetersPerSecond;
        this.eastMetersPerSecond = eastMetersPerSecond;
    }

    public boolean isStructurallyValid() {
        return !workerId.isEmpty() && Double.isFinite(latitude) && Double.isFinite(longitude)
            && latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d
            && Float.isFinite(accuracyMeters) && accuracyMeters > 0f;
    }

    public boolean isFresh(long nowMillis) { return nowMillis - observedAtMillis <= 2_000L; }
    public boolean isAccurate() { return accuracyMeters <= (source == Source.UWB_FUSED ? 2f : 8f); }
}
