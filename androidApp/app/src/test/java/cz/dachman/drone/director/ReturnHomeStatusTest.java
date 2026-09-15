package cz.dachman.drone.director;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class ReturnHomeStatusTest {
    @Test public void verifiedConfigurationUnlocksTakeoff() {
        ReturnHomeStatus status = ReturnHomeStatus.verified(49.66812, 16.08961, 1_000L,
            1.5d, 35, true, true);
        assertTrue(status.ready);
    }

    @Test public void inaccurateHomePointBlocksTakeoff() {
        ReturnHomeStatus status = ReturnHomeStatus.verified(49.66812, 16.08961, 1_000L,
            11d, 35, true, true);
        assertFalse(status.ready);
    }

    @Test public void missingSmartRthOrFailsafeBlocksTakeoff() {
        assertFalse(ReturnHomeStatus.verified(49.66812, 16.08961, 1_000L,
            1d, 35, false, true).ready);
        assertFalse(ReturnHomeStatus.verified(49.66812, 16.08961, 1_000L,
            1d, 35, true, false).ready);
    }

    @Test public void distanceCalculationIsFinite() {
        double distance = ReturnHomeStatus.distanceMeters(49.66812, 16.08961, 49.66813, 16.08962);
        assertTrue(distance > 0d && distance < 5d);
    }
}
