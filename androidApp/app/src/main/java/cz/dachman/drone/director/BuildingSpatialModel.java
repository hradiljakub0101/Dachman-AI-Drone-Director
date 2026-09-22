package cz.dachman.drone.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic survey model anchored to DJI GPS/Home Point and a reviewed building polygon. */
public final class BuildingSpatialModel {
    public final List<SiteSafetyPlan.Point> footprint;
    public final int observations;
    public final int coveredSectors;
    public final float minimumAltitude;
    public final float maximumAltitude;
    public final long updatedAtMillis;

    BuildingSpatialModel(List<SiteSafetyPlan.Point> footprint, int observations, int coveredSectors,
            float minimumAltitude, float maximumAltitude, long updatedAtMillis) {
        this.footprint = Collections.unmodifiableList(new ArrayList<>(footprint));
        this.observations = observations;
        this.coveredSectors = coveredSectors;
        this.minimumAltitude = minimumAltitude;
        this.maximumAltitude = maximumAltitude;
        this.updatedAtMillis = updatedAtMillis;
    }

    public boolean ready() { return footprint.size() >= 3 && observations >= 40 && coveredSectors >= 6; }
    public int confidencePercent() {
        int sampleScore = Math.min(60, observations);
        int sectorScore = Math.min(40, coveredSectors * 5);
        return Math.min(100, sampleScore + sectorScore);
    }

    public static final class Builder {
        private final List<SiteSafetyPlan.Point> footprint;
        private final boolean[] sectors = new boolean[8];
        private int observations;
        private float minimumAltitude = Float.POSITIVE_INFINITY;
        private float maximumAltitude = Float.NEGATIVE_INFINITY;

        public Builder(List<SiteSafetyPlan.Point> footprint) {
            this.footprint = new ArrayList<>(footprint == null ? Collections.emptyList() : footprint);
        }

        public void observe(TelemetrySnapshot telemetry) {
            if (telemetry == null || !telemetry.hasAircraftLocation() || footprint.size() < 3) return;
            SiteSafetyPlan.Point center = centroid(footprint);
            double north = Math.toRadians(telemetry.aircraftLatitude - center.latitude) * 6_371_000d;
            double east = Math.toRadians(telemetry.aircraftLongitude - center.longitude) * 6_371_000d
                * Math.cos(Math.toRadians(center.latitude));
            double bearing = Math.toDegrees(Math.atan2(east, north));
            if (bearing < 0d) bearing += 360d;
            sectors[Math.min(7, (int)(bearing / 45d))] = true;
            observations++;
            minimumAltitude = Math.min(minimumAltitude, telemetry.altitudeMeters);
            maximumAltitude = Math.max(maximumAltitude, telemetry.altitudeMeters);
        }

        public BuildingSpatialModel snapshot(long nowMillis) {
            int covered = 0;
            for (boolean sector : sectors) if (sector) covered++;
            float min = Float.isFinite(minimumAltitude) ? minimumAltitude : 0f;
            float max = Float.isFinite(maximumAltitude) ? maximumAltitude : 0f;
            return new BuildingSpatialModel(footprint, observations, covered, min, max, nowMillis);
        }

        private static SiteSafetyPlan.Point centroid(List<SiteSafetyPlan.Point> points) {
            double lat = 0d, lon = 0d;
            for (SiteSafetyPlan.Point point : points) { lat += point.latitude; lon += point.longitude; }
            return new SiteSafetyPlan.Point(lat / points.size(), lon / points.size());
        }
    }
}
