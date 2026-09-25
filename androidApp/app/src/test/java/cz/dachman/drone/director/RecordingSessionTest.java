package cz.dachman.drone.director;

import static org.junit.Assert.*;
import org.junit.Test;

public final class RecordingSessionTest {
    @Test public void recStartsWithoutAnyWorkerSelectionOrAiState() {
        RecordingSession session = new RecordingSession();
        assertEquals(RecordingSession.Command.START, session.toggle(true, true));
        assertTrue(session.isRecordingOrStarting());
        assertEquals(RecordingSession.Command.BUSY, session.toggle(true, true));
        session.completed(RecordingSession.Command.START, true);
        assertTrue(session.isRecording());
        assertEquals(RecordingSession.Command.STOP, session.toggle(true, false));
        session.completed(RecordingSession.Command.STOP, true);
        assertFalse(session.isRecording());
    }

    @Test public void photoCannotSwitchCameraModeWhileVideoIsStartingOrRecording() {
        RecordingSession session = new RecordingSession();
        session.toggle(true, true);
        assertTrue(session.isRecordingOrStarting());
        session.cameraState(true);
        session.completed(RecordingSession.Command.START, true);
        assertTrue(session.isRecordingOrStarting());
        session.toggle(true, false);
        assertTrue(session.isRecordingOrStarting());
        session.completed(RecordingSession.Command.STOP, true);
        assertFalse(session.isRecordingOrStarting());
    }

    @Test public void unexpectedCameraStopIsReportedOnceAndPilotCanRestart() {
        RecordingSession session = new RecordingSession();
        session.toggle(true, true);
        session.completed(RecordingSession.Command.START, true);
        assertTrue(session.cameraState(false));
        assertFalse(session.cameraState(false));
        assertEquals(RecordingSession.Command.STORAGE_MISSING, session.toggle(true, false));
        assertEquals(RecordingSession.Command.START, session.toggle(true, true));
    }

    @Test public void failedPilotStopStillShowsRealCameraState() {
        RecordingSession session = new RecordingSession();
        session.toggle(true, true);
        session.completed(RecordingSession.Command.START, true);
        session.toggle(true, true);
        session.completed(RecordingSession.Command.STOP, false);
        assertTrue(session.isRecording());
        assertEquals(RecordingSession.Command.STOP, session.toggle(true, true));
    }

    @Test public void disconnectLeavesActualRecordingUnconfirmed() {
        RecordingSession session = new RecordingSession();
        session.toggle(true, true);
        session.completed(RecordingSession.Command.START, true);
        assertTrue(session.disconnect());
        assertFalse(session.isRecording());
        assertEquals(RecordingSession.Command.CAMERA_MISSING, session.toggle(false, true));
        session.cameraState(true); // On reconnect, the aircraft reports the actual state.
        assertTrue(session.isRecording());
    }
}
