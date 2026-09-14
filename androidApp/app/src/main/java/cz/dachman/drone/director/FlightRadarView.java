package cz.dachman.drone.director;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.view.View;
import java.util.Locale;

/** Compass radar showing aircraft heading and the relative direction of the DJI home point. */
public final class FlightRadarView extends View {
    private static final int GREEN = Color.rgb(43, 232, 171);
    private static final int ORANGE = Color.rgb(255, 171, 64);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private TelemetrySnapshot telemetry = TelemetrySnapshot.disconnected();

    public FlightRadarView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void updateTelemetry(TelemetrySnapshot value) {
        telemetry = value == null ? TelemetrySnapshot.disconnected() : value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.43f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(215, 5, 17, 24));
        paint.setShadowLayer(dp(8), 0, dp(2), Color.BLACK);
        canvas.drawCircle(cx, cy, radius + dp(5), paint);
        paint.clearShadowLayer();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(210, 43, 232, 171));
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.drawCircle(cx, cy, radius * 0.55f, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(9));
        paint.setColor(Color.WHITE);
        canvas.drawText("N", cx, cy - radius + dp(11), paint);
        canvas.drawText("E", cx + radius - dp(8), cy + dp(3), paint);
        canvas.drawText("S", cx, cy + radius - dp(4), paint);
        canvas.drawText("W", cx - radius + dp(8), cy + dp(3), paint);

        drawAircraftArrow(canvas, cx, cy, telemetry.headingDegrees);
        drawHomePoint(canvas, cx, cy, radius);
    }

    private void drawAircraftArrow(Canvas canvas, float cx, float cy, float heading) {
        canvas.save();
        canvas.rotate(heading, cx, cy);
        arrow.reset();
        arrow.moveTo(cx, cy - dp(18));
        arrow.lineTo(cx - dp(8), cy + dp(10));
        arrow.lineTo(cx, cy + dp(6));
        arrow.lineTo(cx + dp(8), cy + dp(10));
        arrow.close();
        paint.setColor(GREEN);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrow, paint);
        canvas.restore();
    }

    private void drawHomePoint(Canvas canvas, float cx, float cy, float radius) {
        if (!telemetry.hasAircraftLocation() || !telemetry.hasHomeLocation()) return;
        float[] result = new float[3];
        Location.distanceBetween(telemetry.aircraftLatitude, telemetry.aircraftLongitude,
            telemetry.homeLatitude, telemetry.homeLongitude, result);
        float relative = result[1] - telemetry.headingDegrees;
        double radians = Math.toRadians(relative);
        float distanceFromCenter = radius * 0.72f;
        float x = cx + (float)Math.sin(radians) * distanceFromCenter;
        float y = cy - (float)Math.cos(radians) * distanceFromCenter;

        paint.setColor(ORANGE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, dp(5), paint);
        paint.setTextSize(dp(7));
        paint.setTextAlign(Paint.Align.CENTER);
        String distance = result[0] >= 1_000f
            ? String.format(Locale.getDefault(), "%.1f km", result[0] / 1_000f)
            : String.format(Locale.getDefault(), "%.0f m", result[0]);
        canvas.drawText(distance, cx, cy + radius + dp(13), paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
