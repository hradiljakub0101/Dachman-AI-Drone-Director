package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class CameraDirectorTest {
    @Test public void workerBelowCompositionTiltsGimbalDown() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.FOLLOW);
        TrackingSnapshot tracking = one(box(0.40f, 0.68f, 0.60f, 0.94f, 1_000L));
        director.begin(plan, tracking);

        CameraDirective command = director.command(plan, tracking);

        assertTrue(command.gimbalPitch < 0f);
        assertTrue(Math.abs(command.gimbalPitch) <= 14f);
    }

    @Test public void workerAboveCompositionTiltsGimbalUp() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.FOLLOW);
        TrackingSnapshot tracking = one(box(0.40f, 0.03f, 0.60f, 0.30f, 1_000L));
        director.begin(plan, tracking);

        assertTrue(director.command(plan, tracking).gimbalPitch > 0f);
    }

    @Test public void smallCentredWorkerZoomsInGraduallyAndNeverPastLimit() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.STATIC_TRACK);
        TrackingSnapshot first = one(box(0.46f, 0.40f, 0.54f, 0.52f, 1_000L));
        director.begin(plan, first);
        float previous = director.command(plan, first).digitalZoomFactor;

        for (int index = 1; index <= 30; index++) {
            TrackingSnapshot next = one(box(0.46f, 0.40f, 0.54f, 0.52f,
                1_000L + index * 200L));
            float zoom = director.command(plan, next).digitalZoomFactor;
            assertTrue(zoom >= previous);
            assertTrue(zoom <= CameraDirector.MAX_DIGITAL_ZOOM);
            previous = zoom;
        }
        assertTrue(previous > 1.25f);
    }

    @Test public void targetAtFrameEdgeForcesZoomOut() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.STATIC_TRACK);
        TrackingSnapshot small = one(box(0.46f, 0.40f, 0.54f, 0.52f, 1_000L));
        director.begin(plan, small);
        for (int index = 0; index < 8; index++) {
            director.command(plan, one(box(0.46f, 0.40f, 0.54f, 0.52f,
                1_000L + index * 200L)));
        }
        float zoomed = director.estimatedZoomFactor();

        CameraDirective edge = director.command(plan,
            one(box(0.01f, 0.35f, 0.20f, 0.62f, 3_000L)));

        assertTrue(edge.digitalZoomFactor < zoomed);
        assertTrue(edge.digitalZoomFactor >= CameraDirector.MIN_DIGITAL_ZOOM);
    }

    @Test public void duoModeFramesUnionAndUsesConservativeZoom() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.DUO_FOLLOW);
        TargetBox workerOne = box(0.12f, 0.30f, 0.30f, 0.72f, 1_000L);
        TargetBox workerTwo = box(0.68f, 0.31f, 0.86f, 0.73f, 1_000L);
        TrackingSnapshot tracking = new TrackingSnapshot(Arrays.asList(workerOne, workerTwo),
            workerOne, workerTwo, 1_000L);
        director.begin(plan, tracking);

        CameraDirective command = director.command(plan, tracking);

        assertEquals(0f, command.horizontalError, 0.04f);
        assertTrue(command.digitalZoomFactor <= 1.45f);
    }

    @Test public void orbitModesKeepOppositeLeadRoom() {
        TargetBox centred = box(0.42f, 0.28f, 0.58f, 0.68f, 1_000L);
        TrackingSnapshot tracking = one(centred);
        CameraDirector left = director();
        CameraDirector right = director();
        FlightPlan leftPlan = plan(FlightMode.ORBIT_LEFT);
        FlightPlan rightPlan = plan(FlightMode.ORBIT_RIGHT);
        left.begin(leftPlan, tracking);
        right.begin(rightPlan, tracking);

        assertTrue(left.command(leftPlan, tracking).horizontalError < 0f);
        assertTrue(right.command(rightPlan, tracking).horizontalError > 0f);
    }

    @Test public void motionPredictionCreatesLeadBeforeWorkerReachesEdge() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.FOLLOW);
        TrackingSnapshot first = one(box(0.37f, 0.28f, 0.53f, 0.68f, 1_000L));
        director.begin(plan, first);
        director.command(plan, first);

        CameraDirective moving = director.command(plan,
            one(box(0.47f, 0.28f, 0.63f, 0.68f, 1_200L)));

        assertTrue(moving.horizontalError > 0.05f);
    }

    @Test public void unappliedZoomDoesNotDistortAircraftDistanceEstimate() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.STATIC_TRACK);
        TrackingSnapshot first = one(box(0.46f, 0.40f, 0.54f, 0.52f, 1_000L));
        director.begin(plan, first);
        CameraDirective command = null;
        for (int index = 0; index < 10; index++) {
            command = director.command(plan, one(box(0.46f, 0.40f, 0.54f, 0.52f,
                1_000L + index * 200L)));
        }

        assertTrue(command.digitalZoomFactor > 1f);
        assertEquals(0.12f, command.unzoomedTargetHeight, 0.0001f);
    }

    @Test public void unsupportedZoomStillAllowsGimbalButSendsNoZoomRequest() {
        CameraDirector director = new CameraDirector();
        FlightPlan plan = plan(FlightMode.FOLLOW);
        TrackingSnapshot tracking = one(box(0.42f, 0.64f, 0.58f, 0.92f, 1_000L));
        director.begin(plan, tracking);

        CameraDirective command = director.command(plan, tracking);

        assertTrue(command.gimbalPitch < 0f);
        assertTrue(Float.isNaN(command.digitalZoomFactor));
    }

    @Test public void missingTargetProducesNoCameraMovementOrZoomCommand() {
        CameraDirector director = director();
        FlightPlan plan = plan(FlightMode.FOLLOW);
        TrackingSnapshot empty = TrackingSnapshot.empty();
        director.begin(plan, empty);

        CameraDirective command = director.command(plan, empty);

        assertEquals(0f, command.gimbalPitch, 0f);
        assertTrue(Float.isNaN(command.digitalZoomFactor));
    }

    private static FlightPlan plan(FlightMode mode) {
        return new FlightPlan(mode, FlightLevel.WIDE, FlightProfile.STANDARD);
    }

    private static CameraDirector director() {
        CameraDirector director = new CameraDirector();
        director.updateAppliedZoom(true, CameraDirector.MIN_DIGITAL_ZOOM);
        return director;
    }

    private static TrackingSnapshot one(TargetBox target) {
        return new TrackingSnapshot(Collections.singletonList(target), target, null,
            target.observedAtMillis);
    }

    private static TargetBox box(float left, float top, float right, float bottom, long time) {
        return new TargetBox(left, top, right, bottom, 0.92f, time);
    }
}
