package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class CameraStorageStatusTest {
    @Test public void healthyCardAllowsRecordingAndKeepsRemainingTime() {
        CameraStorageStatus status = CameraStorageStatus.evaluate(
            true, false, false, true, false, false, true, false, 1_825);

        assertTrue(status.ready);
        assertEquals(1_825, status.remainingRecordingSeconds);
        assertEquals("SD OK", status.shortLabel);
    }

    @Test public void missingCardBlocksRecording() {
        CameraStorageStatus status = CameraStorageStatus.evaluate(
            false, false, false, true, false, false, true, false, 600);

        assertFalse(status.ready);
        assertEquals("SD CHYBÍ", status.shortLabel);
    }

    @Test public void initializingOrFormattingCardBlocksRecording() {
        assertFalse(CameraStorageStatus.evaluate(
            true, true, false, true, false, false, true, false, 600).ready);
        assertFalse(CameraStorageStatus.evaluate(
            true, false, false, true, true, false, true, false, 600).ready);
    }

    @Test public void readOnlyInvalidFullAndErrorCardsBlockRecording() {
        assertFalse(CameraStorageStatus.evaluate(
            true, false, true, true, false, false, true, false, 600).ready);
        assertFalse(CameraStorageStatus.evaluate(
            true, false, false, false, false, false, true, false, 600).ready);
        assertFalse(CameraStorageStatus.evaluate(
            true, false, false, true, false, true, true, false, 600).ready);
        assertFalse(CameraStorageStatus.evaluate(
            true, false, false, true, false, false, true, true, 600).ready);
    }

    @Test public void zeroRemainingTimeBlocksEvenBeforeFullFlagArrives() {
        CameraStorageStatus status = CameraStorageStatus.evaluate(
            true, false, false, true, false, false, true, false, 0);

        assertFalse(status.ready);
        assertEquals("SD PLNÁ", status.shortLabel);
    }

    @Test public void unverifiedButWritableCardRemainsUsableWithWarning() {
        CameraStorageStatus status = CameraStorageStatus.evaluate(
            true, false, false, true, false, false, false, false, 300);

        assertTrue(status.ready);
        assertEquals("SD OK?", status.shortLabel);
    }
}
