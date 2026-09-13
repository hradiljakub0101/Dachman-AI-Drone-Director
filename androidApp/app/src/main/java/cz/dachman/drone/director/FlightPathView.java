package cz.dachman.drone.director;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Compact animated trajectory preview; it is visual review, not a map or obstacle model. */
public final class FlightPathView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private FlightPlan plan = new FlightPlan(FlightMode.HOLD, FlightLevel.ROOF, FlightProfile.PRECISE);
    private float phase;

    public FlightPathView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(9, 25, 35));
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(dp(2)); line.setColor(Color.rgb(38, 210, 195));
        drone.setColor(Color.rgb(42, 238, 178));
        target.setColor(Color.rgb(255, 171, 64));
        text.setColor(Color.WHITE); text.setTextSize(dp(12)); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2_800L); animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> { phase = (float)value.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    public void setPlan(FlightPlan plan) { this.plan = plan; phase = 0f; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w * 0.50f, cy = h * 0.53f;
        canvas.drawCircle(cx, cy, dp(8), target);
        if (plan.mode == FlightMode.DUO_FOLLOW) canvas.drawCircle(cx + dp(22), cy, dp(8), target);
        Path path = new Path();
        pointFor(plan.mode, 0f, w, h, path, true);
        for (int i = 1; i <= 60; i++) pointFor(plan.mode, i / 60f, w, h, path, false);
        canvas.drawPath(path, line);
        float[] p = position(plan.mode, phase, w, h);
        canvas.drawCircle(p[0], p[1], dp(7), drone);
        canvas.drawLine(p[0] - dp(12), p[1], p[0] + dp(12), p[1], drone);
        canvas.drawText(plan.mode.label, dp(10), dp(18), text);
        text.setColor(Color.rgb(150, 174, 186));
        canvas.drawText("NÁHLED – NE OBJEKTOVÁ MAPA", dp(10), h - dp(9), text);
        text.setColor(Color.WHITE);
    }

    private void pointFor(FlightMode mode, float t, float w, float h, Path path, boolean start) {
        float[] p = position(mode, t, w, h);
        if (start) path.moveTo(p[0], p[1]); else path.lineTo(p[0], p[1]);
    }

    private float[] position(FlightMode mode, float t, float w, float h) {
        float cx = w * 0.50f, cy = h * 0.53f;
        float radius = Math.min(w, h) * 0.30f;
        switch (mode) {
            case ORBIT_LEFT: {
                double a = Math.PI * 2d * (1d - t) - Math.PI / 2d;
                return new float[]{cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius * 0.55f};
            }
            case ORBIT_RIGHT: {
                double a = Math.PI * 2d * t - Math.PI / 2d;
                return new float[]{cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius * 0.55f};
            }
            case PULL_AWAY: return new float[]{cx, cy - dp(22) - t * h * 0.30f};
            case REVEAL_UP: return new float[]{cx - t * w * 0.24f, cy + dp(45) - t * h * 0.45f};
            case ROPE_MODE: return new float[]{cx + dp(55), h * 0.78f - t * h * 0.52f};
            case FOLLOW:
            case DUO_FOLLOW: return new float[]{w * 0.18f + t * w * 0.64f, cy - dp(38)};
            case STATIC_TRACK:
            case HOLD:
            default: return new float[]{cx, cy - dp(55)};
        }
    }

    @Override protected void onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow(); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
