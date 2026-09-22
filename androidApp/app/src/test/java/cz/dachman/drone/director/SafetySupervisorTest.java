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
            tracking(NOW, true), configuration(), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.ALLOW, decision.action);
    }

    @Test public void targetLossHoldsThenStopsVisualFollow() {
        SafetyDecision hold = safety.evaluate(plan(FlightMode.FOLLOW), healthyTelemetry(),
            tracking(NOW - 1_000L, true), configuration(), NOW, NOW - 1_000L);
        SafetyDecision stop = safety.evaluate(plan(FlightMode.FOLLOW), healthyTelemetry(),
            tracking(NOW - 2_500L, true), configuration(), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.HOLD, hold.action);
        assertEquals(SafetyDecision.Action.STOP, stop.action);
    }

    @Test public void groupModeRequiresTwoVisualTargets() {
        SafetyDecision decision = safety.evaluate(plan(FlightMode.DUO_FOLLOW), healthyTelemetry(),
            tracking(NOW, false), configuration(), NOW, NOW - 1_000L);
        assertEquals(SafetyDecision.Action.HOLD, decision.action);
    }

    @Test public void lowBatteryAndStrongWindStopFlight() {
        TelemetrySnapshot lowBattery = telemetry(20, 10f, "LEVEL_0");
        TelemetrySnapshot strongWind = telemetry(80, 10f, "LEVEL_2");
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(plan(FlightMode.FOLLOW),
            lowBattery, tracking(NOW, true), configuration(), NOW, NOW).action);
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(plan(FlightMode.FOLLOW),
            strongWind, tracking(NOW, true), configuration(), NOW, NOW).action);
    }

    @Test public void selectedHeightCeilingIsEnforced() {
        FlightPlan lowCeiling = new FlightPlan(FlightMode.FOLLOW, FlightLevel.ofMeters(8), FlightProfile.PRECISE);
        assertEquals(SafetyDecision.Action.STOP, safety.evaluate(lowCeiling, telemetry(80, 9f, "LEVEL_0"),
            tracking(NOW, true), configuration(), NOW, NOW).action);
    }

    @Test public void explicitPilotChoiceAllowsOrbitWithoutHomeAndAircraftGps() {
        TelemetrySnapshot noNavigation = new TelemetrySnapshot(true, true, "DJI Mini 2", "ATTI",
            80, 0, 10f, 0.2f, 0f, 90, true, true, false, false, "LEVEL_0",
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0f, false);
        SafetyConfiguration accepted = new SafetyConfiguration(6d, true,
            SiteSafetyPlan.empty(), true);
        assertEquals(SafetyDecision.Action.ALLOW, safety.evaluate(plan(FlightMode.ORBIT_LEFT),
            noNavigation, tracking(NOW, true), accepted, NOW, NOW).action);
    }

    @Test public void missingNavigationRequiresExplicitPilotChoice() {
        TelemetrySnapshot noNavigation = new TelemetrySnapshot(true, true, "DJI Mini 2", "ATTI",
            80, 0, 10f, 0.2f, 0f, 90, true, true, false, false, "LEVEL_0",
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0f, false);
        assertEquals(SafetyDecision.Action.HOLD, safety.evaluate(plan(FlightMode.ORBIT_LEFT),
            noNavigation, tracking(NOW, true), configuration(), NOW, NOW).action);
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
        return new TelemetrySnapshot(true, true, "DJI Mini 2", "GPS", battery, 5, altitude,
            0.2f, 0f, 90, true, true, false, false, wind,
            49.0, 16.0, 49.0, 16.0, 0f, true);
    }

    private static SafetyConfiguration configuration() {
        return new SafetyConfiguration(6d, true, new SiteSafetyPlan(Arrays.asList(
            new SiteSafetyPlan.Point(49.0, 16.0),
            new SiteSafetyPlan.Point(49.0001, 16.0001)), Collections.emptyList(),
            Double.NaN, Double.NaN, 0f, 0f, 0f));
    }
}
