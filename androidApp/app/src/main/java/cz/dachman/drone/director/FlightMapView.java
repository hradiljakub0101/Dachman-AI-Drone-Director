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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

/** Live OpenStreetMap HUD driven exclusively by DJI aircraft and home coordinates. */
public final class FlightMapView extends FrameLayout {
    public enum EditorMode { NONE, ROOF, FORBIDDEN }
    public interface SafetyPlanListener {
        void onMapSafetyPoints(List<SiteSafetyPlan.Point> roof, List<SiteSafetyPlan.Point> forbidden);
    }
    private static final int GREEN = Color.rgb(43, 232, 171);
    private static final OnlineTileSourceBase SATELLITE = new XYTileSource(
        "EsriWorldImagery", 0, 19, 256, ".jpg",
        new String[] { "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" }) {
        @Override public String getTileURLString(long tileIndex) {
            return getBaseUrl() + MapTileIndex.getZoom(tileIndex) + "/"
                + MapTileIndex.getY(tileIndex) + "/" + MapTileIndex.getX(tileIndex) + mImageFilenameEnding;
        }
    };
    private final MapView map;
    private final Marker aircraftMarker;
    private final Marker homeMarker;
    private final Polyline track;
    private final Polygon roofPolygon;
    private final Polygon forbiddenPolygon;
    private final List<GeoPoint> trackPoints = new ArrayList<>();
    private GeoPoint lastTrackPoint;
    private long lastCenterUpdate;
    private EditorMode editorMode = EditorMode.NONE;
    private SafetyPlanListener safetyPlanListener;
    private final List<SiteSafetyPlan.Point> roofPoints = new ArrayList<>();
    private final List<SiteSafetyPlan.Point> forbiddenPoints = new ArrayList<>();

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
        map.setTileSource(SATELLITE);
        map.setMultiTouchControls(true);
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
        homeMarker.setEnabled(false);
        map.getOverlays().add(homeMarker);

        aircraftMarker = new Marker(map);
        aircraftMarker.setTextIcon("▲");
        aircraftMarker.setTitle("DJI Mini 2");
        aircraftMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        aircraftMarker.setEnabled(false);
        map.getOverlays().add(aircraftMarker);

        roofPolygon = polygon(Color.argb(45, 43, 232, 171), GREEN);
        forbiddenPolygon = polygon(Color.argb(75, 234, 77, 89), Color.rgb(234, 77, 89));
        map.getOverlays().add(roofPolygon);
        map.getOverlays().add(forbiddenPolygon);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) {
                return addEditorPoint(point);
            }
            @Override public boolean longPressHelper(GeoPoint point) {
                return addEditorPoint(point);
            }
        }));

        TextView label = hudLabel("ŽIVÁ MAPA", GREEN);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(24), Gravity.TOP | Gravity.LEFT);
        labelParams.setMargins(dp(6), dp(6), 0, 0);
        addView(label, labelParams);

        TextView attribution = hudLabel("© Esri • satelitní mapa", Color.WHITE);
        FrameLayout.LayoutParams attributionParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(20), Gravity.BOTTOM | Gravity.RIGHT);
        attributionParams.setMargins(0, 0, dp(4), dp(3));
        addView(attribution, attributionParams);
    }

    public void updateTelemetry(TelemetrySnapshot telemetry) {
        if (telemetry == null) return;
        if (telemetry.hasHomeLocation()) {
            GeoPoint home = new GeoPoint(telemetry.homeLatitude, telemetry.homeLongitude);
            homeMarker.setPosition(home);
            homeMarker.setEnabled(true);
            if (!telemetry.hasAircraftLocation()) map.getController().animateTo(home);
        } else {
            homeMarker.setEnabled(false);
        }
        if (!telemetry.hasAircraftLocation()) { map.invalidate(); return; }
        GeoPoint aircraft = new GeoPoint(telemetry.aircraftLatitude, telemetry.aircraftLongitude);
        aircraftMarker.setEnabled(true);
        aircraftMarker.setPosition(aircraft);
        aircraftMarker.setRotation(-telemetry.headingDegrees);
        aircraftMarker.setSnippet(String.format(java.util.Locale.getDefault(),
            "Výška %.1f m", telemetry.altitudeMeters));


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

    public void setEditorMode(EditorMode mode) { editorMode = mode == null ? EditorMode.NONE : mode; }
    public EditorMode editorMode() { return editorMode; }
    public void setSafetyPlanListener(SafetyPlanListener listener) { safetyPlanListener = listener; }
    public void clearSafetyPoints() {
        roofPoints.clear(); forbiddenPoints.clear(); renderSafetyPolygons();
        if (safetyPlanListener != null) safetyPlanListener.onMapSafetyPoints(
            new ArrayList<>(roofPoints), new ArrayList<>(forbiddenPoints));
    }

    private boolean addEditorPoint(GeoPoint point) {
        if (editorMode == EditorMode.NONE || point == null) return false;
        SiteSafetyPlan.Point next = new SiteSafetyPlan.Point(point.getLatitude(), point.getLongitude());
        if (editorMode == EditorMode.ROOF) roofPoints.add(next); else forbiddenPoints.add(next);
        renderSafetyPolygons();
        if (safetyPlanListener != null) safetyPlanListener.onMapSafetyPoints(
            new ArrayList<>(roofPoints), new ArrayList<>(forbiddenPoints));
        return true;
    }

    private void renderSafetyPolygons() {
        roofPolygon.setPoints(toGeoPoints(roofPoints));
        forbiddenPolygon.setPoints(toGeoPoints(forbiddenPoints));
        map.invalidate();
    }

    private static List<GeoPoint> toGeoPoints(List<SiteSafetyPlan.Point> input) {
        List<GeoPoint> output = new ArrayList<>();
        for (SiteSafetyPlan.Point point : input) output.add(new GeoPoint(point.latitude, point.longitude));
        return output;
    }

    private static Polygon polygon(int fill, int stroke) {
        Polygon polygon = new Polygon();
        polygon.getFillPaint().setColor(fill);
        polygon.getOutlinePaint().setColor(stroke);
        polygon.getOutlinePaint().setStrokeWidth(4f);
        return polygon;
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
