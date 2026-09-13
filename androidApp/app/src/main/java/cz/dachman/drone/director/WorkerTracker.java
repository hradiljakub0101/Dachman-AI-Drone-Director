package cz.dachman.drone.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

/** Offline COCO person detection with identity continuity based on overlap and centre distance. */
public final class WorkerTracker {
    public interface Listener {
        void onTrackerReady();
        void onTracking(TrackingSnapshot snapshot);
        void onTrackerError(String message);
    }

    private static final String MODEL = "efficientdet-lite0.tflite";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile ObjectDetector detector;
    private volatile TrackingSnapshot snapshot = TrackingSnapshot.empty();
    private volatile TargetBox primaryLock;
    private volatile TargetBox secondaryLock;
    private volatile boolean closed;
    private Listener listener;

    public WorkerTracker(Context context, Listener listener) {
        this.listener = listener;
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            try {
                ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(5)
                    .setScoreThreshold(0.35f)
                    .setLabelAllowList(Collections.singletonList("person"))
                    .setNumThreads(2)
                    .build();
                detector = ObjectDetector.createFromFileAndOptions(app, MODEL, options);
                main.post(() -> { if (!closed && this.listener != null) this.listener.onTrackerReady(); });
            } catch (Exception error) {
                main.post(() -> { if (!closed && this.listener != null) this.listener.onTrackerError("AI model nelze načíst: " + safe(error)); });
            }
        });
    }

    public boolean isReady() { return detector != null && !closed; }
    public boolean isProcessing() { return processing.get(); }
    public TrackingSnapshot snapshot() { return snapshot; }

    public void analyze(Bitmap bitmap) {
        ObjectDetector currentDetector = detector;
        if (closed || currentDetector == null || !processing.compareAndSet(false, true)) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            return;
        }
        executor.execute(() -> {
            try {
                List<Detection> raw = currentDetector.detect(TensorImage.fromBitmap(bitmap));
                long now = SystemClock.elapsedRealtime();
                List<TargetBox> candidates = new ArrayList<>();
                for (Detection detection : raw) {
                    List<Category> categories = detection.getCategories();
                    if (categories.isEmpty()) continue;
                    Category category = categories.get(0);
                    if (!"person".equalsIgnoreCase(category.getLabel())) continue;
                    RectF box = detection.getBoundingBox();
                    candidates.add(new TargetBox(box.left / bitmap.getWidth(), box.top / bitmap.getHeight(),
                        box.right / bitmap.getWidth(), box.bottom / bitmap.getHeight(), category.getScore(), now));
                }
                TargetBox nextPrimary = match(primaryLock, candidates, null);
                TargetBox nextSecondary = match(secondaryLock, candidates, nextPrimary);
                if (primaryLock != null) primaryLock = nextPrimary == null ? primaryLock : nextPrimary;
                if (secondaryLock != null) secondaryLock = nextSecondary == null ? secondaryLock : nextSecondary;
                snapshot = new TrackingSnapshot(candidates, primaryLock, secondaryLock, now);
                TrackingSnapshot published = snapshot;
                main.post(() -> { if (!closed && listener != null) listener.onTracking(published); });
            } catch (Exception error) {
                main.post(() -> { if (!closed && listener != null) listener.onTrackerError("AI analýza selhala: " + safe(error)); });
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                processing.set(false);
            }
        });
    }

    /** Selects the closest currently detected person. Slot one is primary, slot two secondary. */
    public boolean selectAt(int slot, float normalizedX, float normalizedY) {
        List<TargetBox> candidates = snapshot.candidates;
        TargetBox selected = null;
        float best = Float.MAX_VALUE;
        for (TargetBox candidate : candidates) {
            float distance = distance(candidate.centerX(), candidate.centerY(), normalizedX, normalizedY);
            if (candidate.contains(normalizedX, normalizedY)) distance *= 0.2f;
            if (distance < best) { best = distance; selected = candidate; }
        }
        if (selected == null || best > 0.28f) return false;
        if (slot == 1) {
            if (sameCandidate(selected, secondaryLock)) return false;
            primaryLock = selected;
        } else {
            if (sameCandidate(selected, primaryLock)) return false;
            secondaryLock = selected;
        }
        snapshot = new TrackingSnapshot(candidates, primaryLock, secondaryLock, snapshot.processedAtMillis);
        if (listener != null) listener.onTracking(snapshot);
        return true;
    }

    public void clearSelections() {
        primaryLock = null;
        secondaryLock = null;
        snapshot = new TrackingSnapshot(snapshot.candidates, null, null, snapshot.processedAtMillis);
        if (listener != null) listener.onTracking(snapshot);
    }

    private static TargetBox match(TargetBox locked, List<TargetBox> candidates, TargetBox excluded) {
        if (locked == null) return null;
        TargetBox bestTarget = null;
        float bestScore = -1f;
        for (TargetBox candidate : candidates) {
            if (sameCandidate(candidate, excluded)) continue;
            float distance = locked.centerDistance(candidate);
            float overlap = locked.intersectionOverUnion(candidate);
            if (distance > 0.32f && overlap < 0.05f) continue;
            float score = overlap * 0.72f + (1f - Math.min(1f, distance)) * 0.28f;
            if (score > bestScore) { bestScore = score; bestTarget = candidate; }
        }
        return bestTarget;
    }

    private static boolean sameCandidate(TargetBox first, TargetBox second) {
        return first != null && second != null && (first == second || first.intersectionOverUnion(second) > 0.72f);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public void close() {
        closed = true;
        listener = null;
        ObjectDetector current = detector;
        detector = null;
        if (current != null) current.close();
        executor.shutdownNow();
    }

    private static String safe(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
