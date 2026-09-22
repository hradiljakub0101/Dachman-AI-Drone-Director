package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class HybridSafetyTest {
    @Test public void workerPositionDoesNotStopMapDirectedFlight() {
        long now = 10_000L;
        TelemetrySnapshot telemetry = telemetry(49.66812, 16.08961);
        TargetBox box = new TargetBox(.4f, .2f, .6f, .8f, .9f, now);
        TrackingSnapshot tracking = new TrackingSnapshot(java.util.Collections.singletonList(box), box, null, now);
        WorkerPositionFix fix = new WorkerPositionFix("one", 49.66813, 16.08961, 1f, now,
            WorkerPositionFix.Source.UWB_FUSED, 0f, 0f);
        WorkerGeoSnapshot geo = new WorkerGeoSnapshot("one", "", fix, null);
        SafetyDecision decision = new SafetySupervisor().evaluate(
            new FlightPlan(FlightMode.FOLLOW, FlightLevel.ofMeters(15), FlightProfile.PRECISE),
            telemetry, tracking, geo, now, now);
        assertEquals(SafetyDecision.Action.ALLOW, decision.action);
    }

    @Test public void staleTagDoesNotHoldMapDirectedFlight() {
        long now = 10_000L;
        TelemetrySnapshot telemetry = telemetry(49.66812, 16.08961);
        TargetBox box = new TargetBox(.4f, .2f, .6f, .8f, .9f, now);
        TrackingSnapshot tracking = new TrackingSnapshot(java.util.Collections.singletonList(box), box, null, now);
        WorkerPositionFix fix = new WorkerPositionFix("one", 49.66825, 16.08961, 1f, 1_000L,
            WorkerPositionFix.Source.UWB_FUSED, 0f, 0f);
        SafetyDecision decision = new SafetySupervisor().evaluate(
            new FlightPlan(FlightMode.FOLLOW, FlightLevel.ofMeters(15), FlightProfile.PRECISE),
            telemetry, tracking, new WorkerGeoSnapshot("one", "", fix, null), now, now);
        assertEquals(SafetyDecision.Action.ALLOW, decision.action);
    }

    private static TelemetrySnapshot telemetry(double latitude, double longitude) {
        return new TelemetrySnapshot(true, true, "Mini 2", "GPS", 80, 15, 12f, 0f, 0f, 100,
            true, true, false, false, "LEVEL_0", latitude, longitude,
            49.668d, 16.089d, 0f, true);
    }
}
