package cz.dachman.drone.director;

import android.graphics.Bitmap;

/** Coarse colour signature of helmet/head, upper clothing and lower clothing. */
public final class AppearanceSignature {
    private final float[] values;

    private AppearanceSignature(float[] values) { this.values = values; }

    public static AppearanceSignature from(Bitmap bitmap, float left, float top, float right, float bottom) {
        if (bitmap == null) return null;
        float[] signature = new float[9];
        for (int region = 0; region < 3; region++) {
            float regionTop = top + (bottom - top) * region / 3f;
            float regionBottom = top + (bottom - top) * (region + 1) / 3f;
            sample(bitmap, left, regionTop, right, regionBottom, signature, region * 3);
        }
        return new AppearanceSignature(signature);
    }

    public float similarity(AppearanceSignature other) {
        if (other == null) return 0.5f;
        float difference = 0f;
        for (int i = 0; i < values.length; i++) difference += Math.abs(values[i] - other.values[i]);
        return Math.max(0f, 1f - difference / values.length);
    }

    private static void sample(Bitmap bitmap, float left, float top, float right, float bottom,
            float[] output, int offset) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int x0 = clamp(Math.round(left * width), 0, width - 1);
        int x1 = clamp(Math.round(right * width), x0 + 1, width);
        int y0 = clamp(Math.round(top * height), 0, height - 1);
        int y1 = clamp(Math.round(bottom * height), y0 + 1, height);
        long red = 0, green = 0, blue = 0, count = 0;
        int stepX = Math.max(1, (x1 - x0) / 8), stepY = Math.max(1, (y1 - y0) / 8);
        for (int y = y0; y < y1; y += stepY) for (int x = x0; x < x1; x += stepX) {
            int color = bitmap.getPixel(x, y);
            red += (color >> 16) & 255; green += (color >> 8) & 255; blue += color & 255; count++;
        }
        if (count == 0) return;
        output[offset] = red / (count * 255f);
        output[offset + 1] = green / (count * 255f);
        output[offset + 2] = blue / (count * 255f);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
