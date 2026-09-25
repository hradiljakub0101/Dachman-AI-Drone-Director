package cz.dachman.drone.director;

/** Camera recording belongs to the pilot, independently of target tracking or flight modes. */
final class RecordingSession {
    enum Command { START, STOP, BUSY, CAMERA_MISSING, STORAGE_MISSING }

    private boolean recording;
    private boolean pending;
    private boolean pilotRequested;

    synchronized Command toggle(boolean cameraConnected, boolean storageReady) {
        if (pending) return Command.BUSY;
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
            if (success) recording = true;
            else pilotRequested = recording;
        } else if (success) {
            recording = false;
        } else {
            pilotRequested = recording;
        }
    }

    /** Returns true once when the aircraft reports an unexpected stop. */
    synchronized boolean cameraState(boolean isRecording) {
        boolean interrupted = recording && !isRecording && pilotRequested && !pending;
        recording = isRecording;
        if (interrupted) pilotRequested = false;
        return interrupted;
    }

    synchronized boolean disconnect() {
        boolean wasRecording = recording || (pending && pilotRequested);
        recording = false;
        pending = false;
        pilotRequested = false;
        return wasRecording;
    }

    synchronized boolean isRecording() { return recording; }
    synchronized boolean isRecordingOrStarting() { return recording || (pending && pilotRequested); }
}
