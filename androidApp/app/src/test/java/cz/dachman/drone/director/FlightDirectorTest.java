package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;

public final class FlightDirectorTest {
    private static final long NOW = 5_000L;

    @Test public void everyModeStaysInsideSelectedProfile() {
        for (FlightMode mode : FlightMode.values()) {
            if (mode == FlightMode.HOLD) continue;
            FlightPlan plan = new FlightPlan(mode, FlightLevel.WIDE, FlightProfile.STANDARD);
            TrackingSnapshot tracking = tracking();
            FlightDirector director = new FlightDirector();
            director.updateAppliedZoom(true, CameraDirector.MIN_DIGITAL_ZOOM);
            director.begin(plan, tracking);
            FlightCommand command = director.command(plan, telemetry(10f), tracking);
            assertTrue(Math.abs(command.pitch) <= FlightProfile.STANDARD.maxHorizontalMetersPerSecond);
            assertTrue(Math.abs(command.roll) <= FlightProfile.STANDARD.maxHorizontalMetersPerSecond);
            assertTrue(Math.abs(command.vertical) <= FlightProfile.STANDARD.maxVerticalMetersPerSecond);
            assertTrue(Math.abs(command.yaw) <= FlightProfile.STANDARD.maxYawDegreesPerSecond);
            assertTrue(Math.abs(command.gimbalPitch) <= 20f);
            assertTrue(command.digitalZoomFactor >= CameraDirector.MIN_DIGITAL_ZOOM);
            assertTrue(command.digitalZoomFactor <= CameraDirector.MAX_DIGITAL_ZOOM);
        }
    }

    @Test public void heightCeilingBlocksFurtherClimb() {
        FlightPlan plan = new FlightPlan(FlightMode.REVEAL_UP, FlightLevel.ofMeters(8), FlightProfile.CINEMATIC);
        FlightDirector director = new FlightDirector();
        director.begin(plan, tracking());
        assertEquals(0f, director.command(plan, telemetry(8f), tracking()).vertical, 0.0001f);
    }

    @Test public void minimumHeightBlocksDescentInRopeMode() {
        FlightPlan plan = new FlightPlan(FlightMode.ROPE_MODE, FlightLevel.WORKSITE, FlightProfile.STANDARD);
        TargetBox lowInFrame = new TargetBox(0.4f, 0.65f, 0.6f, 0.95f, 0.9f, NOW);
        TrackingSnapshot tracking = new TrackingSnapshot(Collections.singletonList(lowInFrame), lowInFrame, null, NOW);
        FlightDirector director = new FlightDirector();
        director.begin(plan, tracking);
        assertEquals(0f, director.command(plan, telemetry(2f), tracking).vertical, 0.0001f);
    }

    @Test public void flightLevelCoversEveryMeterAndClampsBounds() {
        assertEquals(FlightLevel.MINIMUM_METERS, FlightLevel.ofMeters(-50).meters());
        assertEquals(57, FlightLevel.ofMeters(57).meters());
        assertEquals(FlightLevel.MAXIMUM_METERS, FlightLevel.ofMeters(500).meters());
    }

    private static TrackingSnapshot tracking() {
        TargetBox target = new TargetBox(0.58f, 0.30f, 0.78f, 0.70f, 0.9f, NOW);
        return new TrackingSnapshot(Collections.singletonList(target), target, target, NOW);
    }

    private static TelemetrySnapshot telemetry(float altitude) {
        return new TelemetrySnapshot(true, true, "DJI Mini 2", "GPS", 80, 14, altitude,
            0f, 0f, 90, true, true, false, false, "LEVEL_0");
    }
}
