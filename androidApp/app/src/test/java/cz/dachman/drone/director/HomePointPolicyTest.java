package cz.dachman.drone.director;

import static org.junit.Assert.*;
import org.junit.Test;

public final class HomePointPolicyTest {
    @Test public void phonePermissionDoesNotReplaceAircraftPosition() {
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(false), 49, 16, 2f, 0));
    }
    @Test public void freshAccuratePhoneBesideLocatedAircraftCanBeSubmittedToDji() {
        assertNull(HomePointPolicy.phoneBlock(telemetry(true), 49, 16, 5f, 1_000));
    }
    @Test public void staleInaccurateRemoteOrInvalidFixIsRejected() {
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(true), 49, 16, 5f, 11_000));
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(true), 49, 16, 15f, 0));
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(true), 49.01, 16, 5f, 0));
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(true), Double.NaN, 16, 5f, 0));
        assertNotNull(HomePointPolicy.phoneBlock(telemetry(true), 49, 16, -1f, 0));
    }
    @Test public void rejectedReadbackCannotBecomeReady() {
        assertFalse(ReturnHomeStatus.missing("DJI rejected").ready);
        assertFalse(ReturnHomeStatus.verified(49, 16, 1, 30, 30, true, true).ready);
    }
    private static TelemetrySnapshot telemetry(boolean located) {
        return new TelemetrySnapshot(true, true, "Mini 2", "GPS", 80, located ? 5 : 0, 0,
            0, 0, 90, false, false, false, false, "LEVEL_0",
            located ? 49 : Double.NaN, located ? 16 : Double.NaN, 49, 16, 0, true);
    }
}
