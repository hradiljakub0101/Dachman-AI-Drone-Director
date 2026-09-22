package cz.dachman.drone.director;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public final class BuildingSpatialModelTest {
    @Test public void surveyNeedsSamplesFromMultipleSectors() {
        BuildingSpatialModel.Builder builder = new BuildingSpatialModel.Builder(Arrays.asList(
            new SiteSafetyPlan.Point(49.0, 16.0), new SiteSafetyPlan.Point(49.0, 16.0001),
            new SiteSafetyPlan.Point(49.0001, 16.0001), new SiteSafetyPlan.Point(49.0001, 16.0)));
        assertFalse(builder.snapshot(0L).ready());
        double centerLat = 49.00005, centerLon = 16.00005;
        for (int sector = 0; sector < 8; sector++) {
            double angle = Math.toRadians(sector * 45d);
            for (int sample = 0; sample < 6; sample++) {
                builder.observe(telemetry(centerLat + Math.cos(angle) * 0.0002,
                    centerLon + Math.sin(angle) * 0.0003));
            }
        }
        BuildingSpatialModel model = builder.snapshot(1_000L);
        assertTrue(model.ready());
        assertTrue(model.confidencePercent() >= 70);
    }

    @Test public void brakingDecaysInsteadOfContinuingBlindly() {
        FlightCommand moving = new FlightCommand(2f, -1f, 10f, .5f, -5f, 1f);
        FlightCommand halfway = FlightRuntime.brakingCommand(moving, 750L);
        assertTrue(Math.abs(halfway.pitch) < Math.abs(moving.pitch));
        assertTrue(FlightRuntime.brakingCommand(moving, 1_500L).isZero());
    }

    private static TelemetrySnapshot telemetry(double latitude, double longitude) {
        return new TelemetrySnapshot(true, true, "Mini 2", "GPS", 80, 10, 12f, 0f, 0f, 100,
            true, true, false, false, "LEVEL_0", latitude, longitude,
            49d, 16d, 0f, true);
    }
}
