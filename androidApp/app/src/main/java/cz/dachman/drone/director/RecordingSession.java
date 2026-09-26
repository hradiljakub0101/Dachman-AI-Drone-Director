package cz.dachman.drone.director;

/** Camera recording belongs to the pilot, independently of target tracking or flight modes. */
final class RecordingSession {
    enum Command { START, STOP, BUSY, CAMERA_MISSING, STORAGE_MISSING }

    private boolean recording;
    private boolean pending;
    private boolean awaitingStartConfirmation;
    private boolean pilotRequested;

    synchronized Command toggle(boolean cameraConnected, boolean storageReady) {
        if (pending || awaitingStartConfirmation) return Command.BUSY;
        if (!cameraConnected) return Command.CAMERA_MISSING;
        if (recording) {
            pending = true;
            pilotRequested = false;
            return Command.STOP;
        }
        if (!storageReady) return Command.STORAGE_MISSING;
        pending = true;
        pilotRequested = true;
        return Command.START;
    }

    synchronized void completed(Command command, boolean success) {
        if (command != Command.START && command != Command.STOP) return;
        pending = false;
        if (command == Command.START) {
            if (!success) {
                awaitingStartConfirmation = false;
                pilotRequested = false;
                recording = false;
            } else {
                awaitingStartConfirmation = !recording;
            }
        } else {
            awaitingStartConfirmation = false;
            if (success) recording = false;
            else pilotRequested = recording;
        }
    }

    /** Returns true only after DJI camera state confirms video is actually recording. */
    synchronized boolean cameraState(boolean isRecording) {
        if (isRecording) {
            recording = true;
            awaitingStartConfirmation = false;
            pilotRequested = true;
            return false;
        }
        // Ignore a stale "not recording" event while START is in flight or being confirmed.
        if (awaitingStartConfirmation || (pending && pilotRequested)) return false;
        boolean interrupted = recording && pilotRequested && !pending;
        recording = false;
        if (interrupted) pilotRequested = false;
        return interrupted;
    }

    synchronized boolean confirmationTimedOut() {
        if (!awaitingStartConfirmation || recording) return false;
        awaitingStartConfirmation = false;
        pilotRequested = false;
        return true;
    }

    synchronized boolean disconnect() {
        boolean wasRecording = recording || awaitingStartConfirmation || (pending && pilotRequested);
        recording = false;
        pending = false;
        awaitingStartConfirmation = false;
        pilotRequested = false;
        return wasRecording;
    }

    synchronized boolean isRecording() { return recording; }
    synchronized boolean isAwaitingStartConfirmation() { return awaitingStartConfirmation; }
    synchronized boolean isRecordingOrStarting() {
        return recording || awaitingStartConfirmation || (pending && pilotRequested);
    }
}
