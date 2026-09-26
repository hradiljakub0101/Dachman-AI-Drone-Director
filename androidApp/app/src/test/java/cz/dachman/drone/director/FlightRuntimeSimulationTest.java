package cz.dachman.drone.director;

import static org.junit.Assert.*;
import android.graphics.SurfaceTexture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Software-in-the-loop only: no radio, SDK, propellers, or production session is reachable. */
public final class FlightRuntimeSimulationTest {
    @Test public void followWorkerOneMovesRightAndUpAndCentresProjection() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.world.workerEast = 3;
            r.world.workerAltitude = 12;
            for (int i = 0; i < 100; i++) r.step();
            assertTrue(r.runtime.isActive());
            assertTrue("Body translated right", r.world.east > 0.2);
            assertTrue("Body climbed", r.world.altitude > 10.1);
            assertTrue("Aircraft yaw changed", r.world.yaw > 3);
            TargetBox target = r.world.project(r.time);
            assertEquals(0.5f, target.centerX(), 0.065f);
            assertEquals(0.5f, target.centerY(), 0.065f);
            assertEquals(100, r.session.packets.size());
        }
    }

    @Test public void staticModeRotatesAndTiltsButDoesNotTranslate() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.STATIC_TRACK, TargetSelection.WORKER_ONE);
            r.world.workerEast = -3;
            r.world.workerAltitude = 8;
            for (int i = 0; i < 80; i++) r.step();
            assertTrue(r.world.yaw < -3);
            assertTrue(r.world.gimbal < -1);
            assertEquals(0d, r.world.east, 0d);
            assertEquals(0d, r.world.north, 0d);
            assertEquals(10d, r.world.altitude, 0d);
            assertEquals(0.5f, r.world.project(r.time).centerX(), 0.065f);
        }
    }

    @Test public void recordingContinuesAcrossFollowHoldAndPilotTakeoverWithoutSecondWorker() {
        try (Rig r = new Rig()) {
            r.session.toggleRecording((success, message) -> assertTrue(success));
            assertTrue(r.session.recording.isRecording());
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            for (int i = 0; i < 10; i++) r.step();
            assertTrue(r.session.recording.isRecording());
            r.runtime.hold("Pilot stopped AI motion");
            assertTrue(r.session.recording.isRecording());
            r.runtime.abort("Pilot took control");
            assertTrue(r.session.recording.isRecording());
            assertEquals(1, r.session.recordingCommands);
            r.session.toggleRecording((success, message) -> assertTrue(success));
            assertFalse(r.session.recording.isRecording());
            assertEquals(2, r.session.recordingCommands);
        }
    }

    @Test public void everyWorkerModeAcceptsOneOrTwoExplicitTargets() {
        for (FlightMode mode : FlightMode.values()) {
            if (!mode.requiresPrimary) continue;
            for (TargetSelection selection : TargetSelection.values()) {
                try (Rig r = new Rig()) {
                    r.selection = selection;
                    r.start(mode, selection);
                    r.world.workerEast = 2;
                    r.step();
                    assertTrue(mode + " / " + selection + " " + r.lastMessage, r.runtime.isActive());
                    assertEquals(1, r.session.packets.size());
                    VirtualStickPacket packet = r.session.packets.get(0);
                    assertTrue(Math.abs(packet.yaw) > 0 || Math.abs(packet.pitch) > 0 || Math.abs(packet.roll) > 0);
                }
            }
        }
    }

    @Test public void missingOrStaleUnselectedWorkerDoesNotBlockWorkerOne() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.world.workerEast = 3;
            r.time += 100;
            r.runtime.updateTelemetry(r.telemetry());
            TargetBox primary = r.world.project(r.time);
            TargetBox staleSecondary = new TargetBox(.1f, .2f, .3f, .6f, .1f, 1L);
            r.runtime.updateTracking(new TrackingSnapshot(Collections.singletonList(primary), primary, staleSecondary, r.time));
            r.runtime.tick();
            assertTrue(r.runtime.isActive());
            assertTrue(r.session.last.roll > 0f);
        }
    }

    @Test public void bothSelectionDoesNotSilentlyFallBackToOneWorker() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.BOTH);
            assertFalse(r.runtime.isActive());
            assertTrue(r.session.packets.isEmpty());
        }
    }

    @Test public void modesProduceTheDocumentedDjiVelocityAxes() {
        for (FlightMode mode : new FlightMode[]{FlightMode.ORBIT_LEFT, FlightMode.ORBIT_RIGHT,
                FlightMode.SURVEY_MAP, FlightMode.PULL_AWAY, FlightMode.REVEAL_UP}) {
            try (Rig r = new Rig()) {
                r.start(mode, TargetSelection.WORKER_ONE);
                for (int i = 0; i < 10; i++) r.step();
                VirtualStickPacket packet = r.session.packets.get(0);
                if (mode == FlightMode.PULL_AWAY || mode == FlightMode.REVEAL_UP) {
                    assertTrue("SDK roll must command backwards", packet.roll < 0);
                    assertEquals("No unintended lateral movement", 0f, packet.pitch, .0001f);
                    assertTrue(r.world.north < -.1);
                    assertTrue(r.world.altitude > 10);
                } else {
                    assertTrue(mode == FlightMode.ORBIT_LEFT ? packet.pitch < 0 : packet.pitch > 0);
                    assertTrue(Math.abs(r.world.east) > .1);
                }
            }
        }
    }

    @Test public void groundHomeBatteryAndLinkPreventEnabling() {
        for (int condition = 0; condition < 4; condition++) {
            try (Rig r = new Rig()) {
                if (condition == 0) r.flying = false;
                if (condition == 1) r.home = false;
                if (condition == 2) r.battery = 18;
                if (condition == 3) r.connected = false;
                r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
                assertFalse(r.runtime.isActive());
                assertEquals(0, r.session.enableCalls);
                assertTrue(r.session.packets.isEmpty());
            }
        }
    }

    @Test public void explicitHomeUnavailableModeAllowsEveryAutonomousMovementWithoutHomeOrGps() {
        for (FlightMode mode : FlightMode.values()) {
            if (mode == FlightMode.HOLD || mode == FlightMode.HOME_APPROACH) continue;
            try (Rig r = new Rig()) {
                r.selection = mode.requiresSecondary ? TargetSelection.BOTH : TargetSelection.WORKER_ONE;
                r.home = false;
                r.location = false;
                r.runtime.updateSafetyConfiguration(new SafetyConfiguration(6d, true,
                    SiteSafetyPlan.empty(), true));
                r.start(mode, mode.requiresSecondary ? TargetSelection.BOTH : TargetSelection.WORKER_ONE);
                assertTrue(mode + ": " + r.lastMessage, r.runtime.isActive());
                r.step();
                assertFalse(r.session.packets.isEmpty());
            }
        }
    }

    @Test public void missingHomeStillBlocksMovementUntilPilotSelectsUnavailableMode() {
        try (Rig r = new Rig()) {
            r.home = false;
            r.location = false;
            r.start(FlightMode.ORBIT_LEFT, TargetSelection.WORKER_ONE);
            assertFalse(r.runtime.isActive());
            assertEquals(0, r.session.enableCalls);
        }
    }

    @Test public void lossOfLocalisationStopsWithoutInventingPhonePosition() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.world.workerEast = 3;
            r.step();
            r.location = false;
            for (int i = 0; i < 30; i++) r.step();
            assertFalse(r.runtime.isActive());
            assertTrue(r.session.last.isZero());
        }
    }

    @Test public void targetLossHoldsThenStopsAndDoesNotAutoRestart() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.refreshTarget = false;
            for (int i = 0; i < 12; i++) r.step();
            assertTrue(r.runtime.isActive());
            assertTrue(r.session.last.isZero());
            for (int i = 0; i < 14; i++) r.step();
            assertFalse(r.runtime.isActive());
            r.refreshTarget = true;
            r.step();
            assertFalse(r.runtime.isActive());
        }
    }

    @Test public void sdkRejectionAndMissingAcknowledgementsStopTheLoop() {
        for (boolean reject : new boolean[]{true, false}) {
            try (Rig r = new Rig()) {
                r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
                r.session.reject = reject;
                r.session.acknowledge = reject;
                for (int i = 0; i < 18; i++) r.step();
                assertFalse(r.runtime.isActive());
                assertTrue(r.session.last.isZero());
                assertTrue(r.session.disableCalls > 0);
            }
        }
    }

    @Test public void staleTelemetryAndRthStopCommands() {
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.time += 1_600;
            r.runtime.tick();
            assertFalse(r.runtime.isActive());
        }
        try (Rig r = new Rig()) {
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.rth = true;
            r.step();
            assertFalse(r.runtime.isActive());
        }
    }

    @Test public void cancelledEnableAndChangedPreflightNeverActivate() {
        try (Rig r = new Rig()) {
            r.session.delayEnable = true;
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.runtime.abort("Pilot převzal");
            r.session.pendingEnable.onComplete(true, "late success");
            assertFalse(r.runtime.isActive());
            r.step();
            assertTrue(r.session.last.isZero());
        }
        try (Rig r = new Rig()) {
            r.session.delayEnable = true;
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.battery = 18;
            r.runtime.updateTelemetry(r.telemetry());
            r.session.pendingEnable.onComplete(true, "late success");
            assertFalse(r.runtime.isActive());
        }
    }

    @Test public void pilotOverrideIsLatchedAndDoesNotRestartOnTargetUpdate() {
        try (Rig r = new Rig()) {
            ControlAuthority authority = new ControlAuthority();
            authority.targetConfirmed(true);
            authority.activateAi();
            r.start(FlightMode.FOLLOW, TargetSelection.WORKER_ONE);
            r.step();
            authority.pilotTookOver();
            r.runtime.abort("RC stick");
            int count = r.session.packets.size();
            for (int i = 0; i < 10; i++) r.step();
            assertEquals(count, r.session.packets.size());
            assertFalse(authority.targetConfirmed(true));
            assertFalse(authority.activateAi());
            assertTrue(authority.confirmManualRearm(true));
            assertTrue(authority.activateAi());
        }
    }

    @Test public void heightCeilingIsAppliedAfterSmoothing() {
        FlightDirector director = new FlightDirector();
        FlightPlan plan = new FlightPlan(FlightMode.REVEAL_UP, FlightLevel.ofMeters(10), FlightProfile.STANDARD);
        try (Rig r = new Rig()) {
            r.world.altitude = 9;
            director.begin(plan, r.telemetry(), r.tracking(), SafetyConfiguration.defaults());
            assertTrue(director.command(plan, r.telemetry(), r.tracking()).vertical > 0);
            r.world.altitude = 10;
            assertEquals(0f, director.command(plan, r.telemetry(), r.tracking()).vertical, 0f);
        }
    }

    @Test public void orbitPullAwayAndRevealStartWithoutWorkerTags() {
        for (FlightMode mode : new FlightMode[]{FlightMode.ORBIT_LEFT, FlightMode.ORBIT_RIGHT,
                FlightMode.PULL_AWAY, FlightMode.REVEAL_UP}) {
            try (Rig r = new Rig()) {
                r.noTargets = true;
                r.start(mode, TargetSelection.WORKER_ONE);
                assertTrue(mode + " should not be blocked by missing worker tags: " + r.lastMessage,
                    r.runtime.isActive());
                r.step();
                assertEquals(1, r.session.packets.size());
                VirtualStickPacket packet = r.session.packets.get(0);
                assertTrue(mode + " should issue a non-zero camera move",
                    Math.abs(packet.pitch) > 0 || Math.abs(packet.roll) > 0
                        || Math.abs(packet.yaw) > 0 || Math.abs(packet.vertical) > 0);
            }
        }
    }

    private static final class Rig implements AutoCloseable {
        long time = 10_000;
        int battery = 80;
        boolean flying = true, home = true, connected = true, location = true, rth;
        boolean refreshTarget = true;
        boolean noTargets;
        TargetSelection selection = TargetSelection.WORKER_ONE;
        String lastMessage;
        final World world = new World();
        final FakeSession session = new FakeSession();
        final FlightRuntime runtime = new FlightRuntime(session,
            (active, message, command) -> lastMessage = message, () -> time, false);

        void start(FlightMode mode, TargetSelection targets) {
            runtime.updateTelemetry(telemetry());
            runtime.updateTracking(tracking());
            runtime.start(new FlightPlan(mode, FlightLevel.ofMeters(30), FlightProfile.STANDARD, targets),
                (success, message) -> lastMessage = message);
        }

        void step() {
            time += 100;
            runtime.updateTelemetry(telemetry());
            if (refreshTarget) runtime.updateTracking(tracking());
            runtime.tick();
            if (session.enabled) world.integrate(VirtualStickPacket.from(session.last), session.last.gimbalPitch);
        }

        TrackingSnapshot tracking() {
            if (noTargets) return new TrackingSnapshot(Collections.emptyList(), null, null, time);
            TargetBox first = world.project(time);
            TargetBox second = new TargetBox(first.left + .05f, first.top,
                first.right + .05f, first.bottom, .9f, time);
            return new TrackingSnapshot(Arrays.asList(first, second),
                selection == TargetSelection.WORKER_TWO ? null : first,
                selection == TargetSelection.WORKER_ONE ? null : second, time);
        }

        TelemetrySnapshot telemetry() {
            return new TelemetrySnapshot(true, connected, "SIMULATION", "GPS", battery, location ? 12 : 0,
                (float)world.altitude, 0, 0, 90, flying, flying, false, rth, "LEVEL_0",
                location ? 49 + world.north / 111_000d : Double.NaN,
                location ? 16 + world.east / 73_000d : Double.NaN,
                home ? 49 : Double.NaN, home ? 16 : Double.NaN, (float)world.yaw, home);
        }
        @Override public void close() { runtime.close(); }
    }

    /** First-order kinematics and pinhole projection, not a DJI firmware or aerodynamic model. */
    private static final class World {
        double north, east, altitude = 10, yaw, gimbal;
        double workerNorth = 12, workerEast, workerAltitude = 10;
        void integrate(VirtualStickPacket packet, float gimbalSpeed) {
            double heading = Math.toRadians(yaw);
            north += (packet.roll * Math.cos(heading) - packet.pitch * Math.sin(heading)) * .1;
            east += (packet.roll * Math.sin(heading) + packet.pitch * Math.cos(heading)) * .1;
            altitude += packet.vertical * .1;
            yaw += packet.yaw * .1;
            gimbal = Math.max(-90, Math.min(20, gimbal + gimbalSpeed * .1));
        }
        TargetBox project(long now) {
            double n = workerNorth - north, e = workerEast - east;
            double bearing = Math.atan2(e, n) - Math.toRadians(yaw);
            double distance = Math.max(1, Math.hypot(n, e));
            double elevation = Math.atan2(workerAltitude - altitude, distance) - Math.toRadians(gimbal);
            float x = (float)(.5 + Math.tan(bearing) / (2 * Math.tan(Math.toRadians(37.5))));
            float y = (float)(.5 - Math.tan(elevation) / (2 * Math.tan(Math.toRadians(25))));
            float h = (float)(1.7 / distance / (2 * Math.tan(Math.toRadians(25))));
            return new TargetBox(x - h / 4, y - h / 2, x + h / 4, y + h / 2, .95f, now);
        }
    }

    private static final class FakeSession implements DroneSession {
        final List<VirtualStickPacket> packets = new ArrayList<>();
        final RecordingSession recording = new RecordingSession();
        int recordingCommands;
        FlightCommand last = FlightCommand.ZERO;
        int enableCalls, disableCalls;
        boolean enabled, reject, delayEnable, acknowledge = true;
        Completion pendingEnable;
        @Override public void enableVirtualStick(Completion done) {
            enableCalls++;
            if (delayEnable) { pendingEnable = done; return; }
            enabled = true; done.onComplete(true, "Simulated SDK enabled");
        }
        @Override public void sendCommand(FlightCommand command, Completion done) {
            last = command;
            packets.add(VirtualStickPacket.from(command));
            if (acknowledge) done.onComplete(!reject, reject ? "Simulated SDK rejection" : "Simulated ACK");
        }
        @Override public void disableVirtualStick(String reason, Completion done) {
            enabled = false; disableCalls++; done.onComplete(true, reason);
        }
        @Override public void setListener(Listener listener) {}
        @Override public void connect() {}
        @Override public void onUsbAccessoryAttached() {}
        @Override public void attachVideo(SurfaceTexture texture, int width, int height) {}
        @Override public void detachVideo() {}
        @Override public boolean supportsLiveControl() { return false; }
        @Override public void toggleRecording(Completion done) {
            RecordingSession.Command command = recording.toggle(true, true);
            assertTrue(command == RecordingSession.Command.START || command == RecordingSession.Command.STOP);
            recordingCommands++;
            recording.completed(command, true);
            recording.cameraState(command == RecordingSession.Command.START);
            done.onComplete(true, "Simulated camera ACK");
        }
        @Override public void takePhoto(Completion done) { throw new AssertionError("Not a camera simulator"); }
        @Override public void prepareReturnHome(int height, Completion done) { throw new AssertionError("No real Home Point writes"); }
        @Override public void prepareReturnHomeFromDevice(double lat, double lon, float accuracy, int height, Completion done) { throw new AssertionError("No real Home Point writes"); }
        @Override public void startTakeoff(Completion done) { throw new AssertionError("No takeoff"); }
        @Override public void startLanding(Completion done) { throw new AssertionError("No landing"); }
        @Override public void startReturnHome(Completion done) { throw new AssertionError("No real RTH"); }
        @Override public void cancelAircraftAction(Completion done) {}
        @Override public void close() {}
    }
}
