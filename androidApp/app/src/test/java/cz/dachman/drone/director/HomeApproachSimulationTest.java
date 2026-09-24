package cz.dachman.drone.director;

import android.graphics.SurfaceTexture;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Software-in-the-loop physics: this does not emulate DJI firmware, obstacles or GNSS drift. */
public final class HomeApproachSimulationTest {
    private static final double HOME_LAT = 49.66812d, HOME_LON = 16.08961d;

    @Test public void threeDistancesAndHeadingsStopBeforeHomeWithoutLanding() {
        for (int i = 0; i < 3; i++) {
            double distance = new double[]{10d, 14d, 19d}[i];
            float heading = new float[]{0f, 90f, 225f}[i];
            try (Rig rig = new Rig(distance, heading)) {
                rig.start();
                assertTrue("Virtual Stick was not enabled at " + distance + " m: " + rig.message,
                    rig.runtime.isActive());
                for (int step = 0; step < 500 && rig.runtime.isActive(); step++) rig.step();
                assertFalse("Approach must finish in 45 seconds: " + distance, rig.runtime.isActive());
                assertTrue("Progress to Home at " + distance, rig.distance() < distance - 1d);
                assertTrue("Do not fly onto the launch pad", rig.distance() >= 7.95d);
                assertTrue("Stop well before the pad", rig.distance() <= 8.20d);
                assertTrue("No landing command", rig.session.landings == 0);
                assertTrue("Released flight controller", rig.session.disables == 1);
                assertTrue("Final command zero", rig.session.last.isZero());
                assertTrue("Correct body-frame axes sent", rig.session.moved);
            }
        }
    }

    @Test public void homeChangeGpsLossAndPilotOverrideStopWithoutRestart() {
        for (int failure = 0; failure < 3; failure++) {
            try (Rig rig = new Rig(14d, 90f)) {
                rig.start();
                rig.step();
                if (failure == 0) rig.runtime.updateVerifiedHome(ReturnHomeStatus.verified(
                    HOME_LAT + .00001d, HOME_LON, 100L, 0d, 30, true, true));
                if (failure == 1) rig.gps = false;
                if (failure == 2) rig.runtime.abort("PILOT OVERRIDE");
                rig.step();
                assertFalse(rig.runtime.isActive());
                assertTrue(rig.session.last.isZero());
                int count = rig.session.commands;
                for (int j = 0; j < 10; j++) rig.step();
                assertEquals("No reactivation after loss or pilot intervention", count, rig.session.commands);
            }
        }
    }

    @Test public void badDistanceGpsQualityAndUnverifiedHomeNeverEnableSticks() {
        for (int scenario = 0; scenario < 6; scenario++) {
            try (Rig rig = new Rig(scenario == 0 ? 21d : scenario == 1 ? 7d : 14d, 0f)) {
                if (scenario == 2) rig.satellites = 6;
                if (scenario == 3) rig.battery = 20;
                if (scenario == 4) rig.signal = 25;
                if (scenario == 5) rig.runtime.updateVerifiedHome(ReturnHomeStatus.missing("Missing"));
                rig.startWithExistingHome();
                assertFalse("Preflight must fail scenario " + scenario, rig.runtime.isActive());
                assertEquals(0, rig.session.enables);
            }
        }
    }

    @Test public void mappedForbiddenZoneAheadBlocksApproach() {
        List<SiteSafetyPlan.Point> fence = Arrays.asList(
            new SiteSafetyPlan.Point(HOME_LAT + .000040d, HOME_LON - .000010d),
            new SiteSafetyPlan.Point(HOME_LAT + .000080d, HOME_LON - .000010d),
            new SiteSafetyPlan.Point(HOME_LAT + .000080d, HOME_LON + .000010d),
            new SiteSafetyPlan.Point(HOME_LAT + .000040d, HOME_LON + .000010d));
        try (Rig rig = new Rig(14d, 0f)) {
            rig.runtime.updateSafetyConfiguration(new SafetyConfiguration(6d, true,
                new SiteSafetyPlan(new ArrayList<>(), fence, Double.NaN, Double.NaN, 0f, 0f, 0f)));
            rig.start();
            for (int step = 0; step < 200 && rig.runtime.isActive(); step++) rig.step();
            assertFalse(rig.runtime.isActive());
            assertTrue("Should stop before a mapped polygon", rig.distance() > 10d);
            assertTrue(rig.session.last.isZero());
        }
    }

    private static final class Rig implements AutoCloseable {
        long time = 10_000L;
        double north;
        final float heading;
        int satellites = 14, battery = 80, signal = 90;
        boolean gps = true;
        String message;
        final FakeSession session = new FakeSession();
        final FlightRuntime runtime = new FlightRuntime(session,
            (active, note, command) -> message = note, () -> time, false);

        Rig(double startMeters, float heading) {
            this.north = startMeters;
            this.heading = heading;
            runtime.updateVerifiedHome(ReturnHomeStatus.verified(
                HOME_LAT, HOME_LON, 100L, 0d, 30, true, true));
        }
        void start() { startWithExistingHome(); }
        void startWithExistingHome() {
            runtime.updateTelemetry(telemetry());
            runtime.start(new FlightPlan(FlightMode.HOME_APPROACH, FlightLevel.WIDE,
                FlightProfile.PRECISE), (success, note) -> message = note);
        }
        void step() {
            time += 100L;
            runtime.updateTelemetry(telemetry());
            runtime.tick();
            if (session.enabled) {
                VirtualStickPacket command = VirtualStickPacket.from(session.last);
                double angle = Math.toRadians(heading);
                north += (command.roll * Math.cos(angle) - command.pitch * Math.sin(angle)) * .1d;
            }
        }
        double distance() { return Math.abs(north); }
        TelemetrySnapshot telemetry() {
            return new TelemetrySnapshot(true, true, "SIM", "GPS", battery, satellites,
                10f, (float)Math.hypot(session.last.pitch, session.last.roll), 0f,
                signal, true, true, false, false, "LEVEL_0",
                gps ? HOME_LAT + north / 111_195d : Double.NaN,
                gps ? HOME_LON : Double.NaN, HOME_LAT, HOME_LON, heading, true);
        }
        @Override public void close() { runtime.close(); }
    }

    private static final class FakeSession implements DroneSession {
        int enables, disables, landings, commands;
        boolean enabled, moved;
        FlightCommand last = FlightCommand.ZERO;
        @Override public void enableVirtualStick(Completion done) {
            enables++; enabled = true; done.onComplete(true, "enabled");
        }
        @Override public void sendCommand(FlightCommand command, Completion done) {
            last = command; commands++;
            moved |= Math.abs(command.pitch) > 0f || Math.abs(command.roll) > 0f;
            done.onComplete(true, "ACK");
        }
        @Override public void disableVirtualStick(String reason, Completion done) {
            enabled = false; disables++; done.onComplete(true, reason);
        }
        @Override public void setListener(Listener value) {}
        @Override public void connect() {}
        @Override public void onUsbAccessoryAttached() {}
        @Override public void attachVideo(SurfaceTexture value, int width, int height) {}
        @Override public void detachVideo() {}
        @Override public boolean supportsLiveControl() { return false; }
        @Override public void toggleRecording(Completion done) {}
        @Override public void takePhoto(Completion done) {}
        @Override public void prepareReturnHome(int height, Completion done) {}
        @Override public void prepareReturnHomeFromDevice(double lat, double lon,
                float accuracy, int height, Completion done) {}
        @Override public void startTakeoff(Completion done) {}
        @Override public void startLanding(Completion done) { landings++; }
        @Override public void startReturnHome(Completion done) {}
        @Override public void cancelAircraftAction(Completion done) {}
        @Override public void close() {}
    }
}
