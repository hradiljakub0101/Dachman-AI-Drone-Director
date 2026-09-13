package cz.dachman.drone.director;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class TrackingOverlay extends View {
    public interface TapListener { void onTargetTap(float normalizedX, float normalizedY, int selectionSlot); }
    private static final int GREEN = Color.rgb(44, 245, 177);
    private static final int ORANGE = Color.rgb(255, 171, 64);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TrackingSnapshot snapshot = TrackingSnapshot.empty();
    private TapListener tapListener;
    private int selectionSlot = 1;

    public TrackingOverlay(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        boxPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(dp(13));
        setClickable(true);
    }

    public void setTapListener(TapListener tapListener) { this.tapListener = tapListener; }
    public void setSelectionSlot(int slot) { selectionSlot = slot == 2 ? 2 : 1; invalidate(); }
    public int getSelectionSlot() { return selectionSlot; }
    public void setSnapshot(TrackingSnapshot snapshot) { this.snapshot = snapshot; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boxPaint.setStrokeWidth(dp(1.5f));
        boxPaint.setColor(Color.argb(180, 255, 255, 255));
        for (TargetBox candidate : snapshot.candidates) drawBox(canvas, candidate, boxPaint, null);
        if (snapshot.primary != null) {
            boxPaint.setStrokeWidth(dp(3)); boxPaint.setColor(GREEN);
            drawBox(canvas, snapshot.primary, boxPaint, "WORKER 1");
        }
        if (snapshot.secondary != null) {
            boxPaint.setStrokeWidth(dp(3)); boxPaint.setColor(ORANGE);
            drawBox(canvas, snapshot.secondary, boxPaint, "WORKER 2");
        }
        float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
        boxPaint.setColor(Color.argb(150, 255, 255, 255)); boxPaint.setStrokeWidth(dp(1));
        canvas.drawLine(cx - dp(16), cy, cx + dp(16), cy, boxPaint);
        canvas.drawLine(cx, cy - dp(16), cx, cy + dp(16), boxPaint);
        textPaint.setColor(selectionSlot == 1 ? GREEN : ORANGE);
        canvas.drawText("KLEPNI NA WORKER " + selectionSlot, dp(12), getHeight() - dp(14), textPaint);
    }

    private void drawBox(Canvas canvas, TargetBox box, Paint paint, String label) {
        RectF rect = new RectF(box.left * getWidth(), box.top * getHeight(),
            box.right * getWidth(), box.bottom * getHeight());
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        if (label != null) {
            textPaint.setColor(paint.getColor());
            canvas.drawText(label + "  " + Math.round(box.confidence * 100f) + " %",
                rect.left + dp(4), Math.max(dp(16), rect.top - dp(5)), textPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && tapListener != null && getWidth() > 0 && getHeight() > 0) {
            tapListener.onTargetTap(event.getX() / getWidth(), event.getY() / getHeight(), selectionSlot);
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
