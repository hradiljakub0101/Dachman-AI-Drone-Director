package cz.dachman.drone.director;

/** Normalized video-frame rectangle in the range zero to one. */
public final class TargetBox {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final float confidence;
    public final long observedAtMillis;
    public final AppearanceSignature appearance;

    public TargetBox(float left, float top, float right, float bottom, float confidence, long observedAtMillis) {
        this(left, top, right, bottom, confidence, observedAtMillis, null);
    }

    public TargetBox(float left, float top, float right, float bottom, float confidence,
            long observedAtMillis, AppearanceSignature appearance) {
        this.left = clamp01(Math.min(left, right));
        this.top = clamp01(Math.min(top, bottom));
        this.right = clamp01(Math.max(left, right));
        this.bottom = clamp01(Math.max(top, bottom));
        this.confidence = clamp01(confidence);
        this.observedAtMillis = observedAtMillis;
        this.appearance = appearance;
    }

    public float centerX() { return (left + right) * 0.5f; }
    public float centerY() { return (top + bottom) * 0.5f; }
    public float width() { return Math.max(0f, right - left); }
    public float height() { return Math.max(0f, bottom - top); }
    public float area() { return width() * height(); }
    public boolean contains(float x, float y) { return x >= left && x <= right && y >= top && y <= bottom; }

    public float centerDistance(TargetBox other) {
        float dx = centerX() - other.centerX();
        float dy = centerY() - other.centerY();
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public float intersectionOverUnion(TargetBox other) {
        float l = Math.max(left, other.left);
        float t = Math.max(top, other.top);
        float r = Math.min(right, other.right);
        float b = Math.min(bottom, other.bottom);
        float intersection = Math.max(0f, r - l) * Math.max(0f, b - t);
        float union = area() + other.area() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    public static TargetBox union(TargetBox first, TargetBox second) {
        if (first == null) return second;
        if (second == null) return first;
        return new TargetBox(
            Math.min(first.left, second.left), Math.min(first.top, second.top),
            Math.max(first.right, second.right), Math.max(first.bottom, second.bottom),
            Math.min(first.confidence, second.confidence),
            Math.min(first.observedAtMillis, second.observedAtMillis), null);
    }

    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
}
