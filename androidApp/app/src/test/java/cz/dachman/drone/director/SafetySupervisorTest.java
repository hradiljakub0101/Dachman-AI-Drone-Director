package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class SafetySupervisorTest {
    private static final long NOW = 10_000L;
    private final SafetySupervisor safety = new SafetySupervisor();

    @Test public void allowsHealthyFollow() {
        SafetyDecision decision = safety.evaluate(plan(FlightMode.FOLLOW), healthyTelemetry(),
            tracking(NOW, true), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.ALLOW, decision.action);
    }

    @Test public void holdsThenStopsWhenTargetIsLost() {
        SafetyDecision hold = safety.evaluate(plan(FlightMode.FOLLOW), healthyTelemetry(),
            tracking(NOW - 1_000L, true), NOW, NOW - 1_000L);
        SafetyDecision stop = safety.evaluate(plan(FlightMode.FOLLOW), healthyTelemetry(),
            tracking(NOW - 2_500L, true), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.HOLD, hold.action);
        assertEquals(SafetyDecision.Action.STOP, stop.action);
    }

    @Test public void duoFollowRequiresTwoWorkers() {
        SafetyDecision decision = safety.evaluate(plan(FlightMode.DUO_FOLLOW), healthyTelemetry(),
            tracking(NOW, false), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.HOLD, decision.action);
    }

    @Test public void lowBatteryAndStrongWindStopFlight() {
        TelemetrySnapshot lowBattery = telemetry(20, 10f, "LEVEL_0");
        TelemetrySnapshot strongWind = telemetry(80, 10f, "LEVEL_2");
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(plan(FlightMode.FOLLOW),
            lowBattery, tracking(NOW, true), NOW, NOW).action);
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(plan(FlightMode.FOLLOW),
            strongWind, tracking(NOW, true), NOW, NOW).action);
    }

    @Test public void selectedHeightCeilingIsEnforced() {
        FlightPlan lowCeiling = new FlightPlan(FlightMode.FOLLOW, FlightLevel.ofMeters(8), FlightProfile.PRECISE);
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(lowCeiling, telemetry(80, 9f, "LEVEL_0"),
            tracking(NOW, true), NOW, NOW).action);
    }

    private static FlightPlan plan(FlightMode mode) {
        return new FlightPlan(mode, FlightLevel.ofMeters(30), FlightProfile.STANDARD);
    }

    private static TrackingSnapshot tracking(long observedAt, boolean includeSecondary) {
        TargetBox first = new TargetBox(0.2f, 0.2f, 0.4f, 0.8f, 0.9f, observedAt);
        TargetBox second = new TargetBox(0.6f, 0.2f, 0.8f, 0.8f, 0.85f, observedAt);
        return new TrackingSnapshot(includeSecondary ? Arrays.asList(first, second) : Collections.singletonList(first),
            first, includeSecondary ? second : null, observedAt);
    }

    private static TelemetrySnapshot healthyTelemetry() { return telemetry(80, 10f, "LEVEL_0"); }

    private static TelemetrySnapshot telemetry(int battery, float altitude, String wind) {
        return new TelemetrySnapshot(true, true, "DJI Mini 2", "GPS", battery, 14, altitude,
            0.2f, 0f, 90, true, true, false, false, wind);
    }
}
