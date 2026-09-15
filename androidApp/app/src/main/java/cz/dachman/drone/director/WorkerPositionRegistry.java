package cz.dachman.drone.director;

import java.util.HashMap;
import java.util.Map;

/** Stores verified tag updates and binds their stable IDs to Worker one/two. */
public final class WorkerPositionRegistry {
    private final Map<String, WorkerPositionFix> fixes = new HashMap<>();
    private String primaryId = "";
    private String secondaryId = "";

    public synchronized boolean update(String id, double latitude, double longitude, float accuracy,
            long observedAt, WorkerPositionFix.Source source) {
        WorkerPositionFix raw = new WorkerPositionFix(id, latitude, longitude, accuracy, observedAt,
            source, 0f, 0f);
        if (!raw.isStructurallyValid()) return false;
        WorkerPositionFix previous = fixes.get(raw.workerId);
        float north = 0f, east = 0f;
        if (previous != null && observedAt > previous.observedAtMillis) {
            float seconds = (observedAt - previous.observedAtMillis) / 1_000f;
            if (seconds <= 5f) {
                double northDelta = Math.toRadians(latitude - previous.latitude) * 6_371_000d;
                double eastDelta = Math.toRadians(longitude - previous.longitude) * 6_371_000d
                    * Math.cos(Math.toRadians((latitude + previous.latitude) * 0.5d));
                north = smooth(previous.northMetersPerSecond, (float)(northDelta / seconds));
                east = smooth(previous.eastMetersPerSecond, (float)(eastDelta / seconds));
            }
        }
        fixes.put(raw.workerId, new WorkerPositionFix(raw.workerId, latitude, longitude, accuracy,
            observedAt, source, clamp(north, -4f, 4f), clamp(east, -4f, 4f)));
        return true;
    }

    public synchronized boolean bind(int slot, String id) {
        String normalized = id == null ? "" : id.trim();
        if (normalized.isEmpty() || !fixes.containsKey(normalized)) return false;
        if (slot == 1) {
            if (normalized.equals(secondaryId)) return false;
            primaryId = normalized;
        } else {
            if (normalized.equals(primaryId)) return false;
            secondaryId = normalized;
        }
        return true;
    }

    public synchronized void clear() { primaryId = ""; secondaryId = ""; }
    public synchronized WorkerGeoSnapshot snapshot() {
        return new WorkerGeoSnapshot(primaryId, secondaryId, fixes.get(primaryId), fixes.get(secondaryId));
    }
    private static float smooth(float oldValue, float newValue) { return oldValue * 0.65f + newValue * 0.35f; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
