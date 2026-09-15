package cz.dachman.drone.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Roof boundary, no-fly polygon and calibrated roof plane in geographic coordinates. */
public final class SiteSafetyPlan {
    public static final class Point {
        public final double latitude, longitude;
        public Point(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
    }

    public final List<Point> roofBoundary;
    public final List<Point> forbiddenZone;
    public final double referenceLatitude;
    public final double referenceLongitude;
    public final float referenceRoofHeightMeters;
    public final float slopeDegrees;
    public final float downslopeBearingDegrees;

    public SiteSafetyPlan(List<Point> roofBoundary, List<Point> forbiddenZone, double referenceLatitude,
            double referenceLongitude, float referenceRoofHeightMeters, float slopeDegrees,
            float downslopeBearingDegrees) {
        this.roofBoundary = immutable(roofBoundary);
        this.forbiddenZone = immutable(forbiddenZone);
        this.referenceLatitude = referenceLatitude;
        this.referenceLongitude = referenceLongitude;
        this.referenceRoofHeightMeters = referenceRoofHeightMeters;
        this.slopeDegrees = Math.max(0f, Math.min(60f, slopeDegrees));
        this.downslopeBearingDegrees = normalize(downslopeBearingDegrees);
    }

    public static SiteSafetyPlan empty() {
        return new SiteSafetyPlan(Collections.emptyList(), Collections.emptyList(), Double.NaN,
            Double.NaN, 0f, 0f, 0f);
    }
    public boolean hasRoofBoundary() { return roofBoundary.size() >= 3; }
    public boolean hasForbiddenZone() { return forbiddenZone.size() >= 3; }
    public boolean aircraftAllowed(double lat, double lon) {
        return (!hasRoofBoundary() || contains(roofBoundary, lat, lon))
            && (!hasForbiddenZone() || !contains(forbiddenZone, lat, lon));
    }
    public float roofHeightAt(double lat, double lon) {
        if (!Double.isFinite(referenceLatitude) || !Double.isFinite(referenceLongitude)) return Float.NaN;
        double north = Math.toRadians(lat - referenceLatitude) * 6_371_000d;
        double east = Math.toRadians(lon - referenceLongitude) * 6_371_000d
            * Math.cos(Math.toRadians(referenceLatitude));
        double bearing = Math.toRadians(downslopeBearingDegrees);
        double downslopeDistance = north * Math.cos(bearing) + east * Math.sin(bearing);
        return referenceRoofHeightMeters - (float)(downslopeDistance * Math.tan(Math.toRadians(slopeDegrees)));
    }

    static boolean contains(List<Point> polygon, double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Point a = polygon.get(i), b = polygon.get(j);
            boolean crosses = ((a.latitude > lat) != (b.latitude > lat))
                && lon < (b.longitude - a.longitude) * (lat - a.latitude)
                    / ((b.latitude - a.latitude) == 0d ? 1e-12d : b.latitude - a.latitude) + a.longitude;
            if (crosses) inside = !inside;
        }
        return inside;
    }
    private static List<Point> immutable(List<Point> points) {
        return Collections.unmodifiableList(new ArrayList<>(points == null ? Collections.emptyList() : points));
    }
    private static float normalize(float value) { float n = value % 360f; return n < 0f ? n + 360f : n; }
}
