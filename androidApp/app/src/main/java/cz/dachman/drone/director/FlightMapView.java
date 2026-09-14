package cz.dachman.drone.director;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

/** Live OpenStreetMap HUD driven exclusively by DJI aircraft and home coordinates. */
public final class FlightMapView extends FrameLayout {
    private static final int GREEN = Color.rgb(43, 232, 171);
    private static final int ORANGE = Color.rgb(255, 171, 64);
    private final MapView map;
    private final Marker aircraftMarker;
    private final Marker homeMarker;
    private final Polyline track;
    private final List<GeoPoint> trackPoints = new ArrayList<>();
    private GeoPoint lastTrackPoint;
    private long lastCenterUpdate;

    public FlightMapView(Context context) {
        super(context);
        Configuration.getInstance().setUserAgentValue(context.getPackageName());

        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.argb(225, 5, 17, 24));
        frame.setCornerRadius(dp(12));
        frame.setStroke(dp(1), GREEN);
        setBackground(frame);
        setClipToOutline(true);

        map = new MapView(context);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(false);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(18.0);
        addView(map, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        track = new Polyline();
        track.setColor(GREEN);
        track.setWidth(dp(3));
        map.getOverlays().add(track);

        homeMarker = new Marker(map);
        homeMarker.setTextIcon("H");
        homeMarker.setTitle("Domovský bod");
        homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(homeMarker);

        aircraftMarker = new Marker(map);
        aircraftMarker.setTextIcon("▲");
        aircraftMarker.setTitle("DJI Mini 2");
        aircraftMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(aircraftMarker);

        TextView label = hudLabel("ŽIVÁ MAPA", GREEN);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(24), Gravity.TOP | Gravity.LEFT);
        labelParams.setMargins(dp(6), dp(6), 0, 0);
        addView(label, labelParams);

        TextView attribution = hudLabel("© OpenStreetMap", Color.WHITE);
        FrameLayout.LayoutParams attributionParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(20), Gravity.BOTTOM | Gravity.RIGHT);
        attributionParams.setMargins(0, 0, dp(4), dp(3));
        addView(attribution, attributionParams);
    }

    public void updateTelemetry(TelemetrySnapshot telemetry) {
        if (telemetry == null || !telemetry.hasAircraftLocation()) return;
        GeoPoint aircraft = new GeoPoint(telemetry.aircraftLatitude, telemetry.aircraftLongitude);
        aircraftMarker.setPosition(aircraft);
        aircraftMarker.setRotation(-telemetry.headingDegrees);
        aircraftMarker.setSnippet(String.format(java.util.Locale.getDefault(),
            "Výška %.1f m", telemetry.altitudeMeters));

        if (telemetry.hasHomeLocation()) {
            homeMarker.setPosition(new GeoPoint(telemetry.homeLatitude, telemetry.homeLongitude));
            homeMarker.setEnabled(true);
        } else {
            homeMarker.setEnabled(false);
        }

        if (shouldAddTrackPoint(aircraft)) {
            trackPoints.add(aircraft);
            if (trackPoints.size() > 180) trackPoints.remove(0);
            track.setPoints(trackPoints);
            lastTrackPoint = aircraft;
        }

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastCenterUpdate > 900L) {
            map.getController().animateTo(aircraft);
            lastCenterUpdate = now;
        }
        map.invalidate();
    }

    private boolean shouldAddTrackPoint(GeoPoint next) {
        if (lastTrackPoint == null) return true;
        float[] result = new float[1];
        Location.distanceBetween(lastTrackPoint.getLatitude(), lastTrackPoint.getLongitude(),
            next.getLatitude(), next.getLongitude(), result);
        return result[0] >= 0.8f;
    }

    public void onHostResume() { map.onResume(); }
    public void onHostPause() { map.onPause(); }
    public void onHostDestroy() { map.onDetach(); }

    private TextView hudLabel(String value, int color) {
        TextView label = new TextView(getContext());
        label.setText(value);
        label.setTextSize(9);
        label.setTextColor(color);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setPadding(dp(6), 0, dp(6), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(205, 5, 17, 24));
        background.setCornerRadius(dp(8));
        label.setBackground(background);
        return label;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
