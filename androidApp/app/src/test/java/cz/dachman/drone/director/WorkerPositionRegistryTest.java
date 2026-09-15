package cz.dachman.drone.director;

import static org.junit.Assert.*;
import org.junit.Test;

public final class WorkerPositionRegistryTest {
    @Test public void bindsStableIdsAndRejectsDuplicateSlotIdentity() {
        WorkerPositionRegistry registry = new WorkerPositionRegistry();
        long now = 10_000L;
        assertTrue(registry.update("helmet-green", 49.66812, 16.08961, 1.2f, now,
            WorkerPositionFix.Source.UWB_FUSED));
        assertTrue(registry.update("helmet-orange", 49.66813, 16.08962, 1.1f, now,
            WorkerPositionFix.Source.UWB_FUSED));
        assertTrue(registry.bind(1, "helmet-green"));
        assertFalse(registry.bind(2, "helmet-green"));
        assertTrue(registry.bind(2, "helmet-orange"));
        WorkerGeoSnapshot snapshot = registry.snapshot();
        assertTrue(snapshot.requiredReliable(FlightMode.DUO_FOLLOW, now));
    }

    @Test public void rejectsStaleOrInaccuratePhonePosition() {
        WorkerPositionRegistry registry = new WorkerPositionRegistry();
        assertTrue(registry.update("phone-one", 49.66812, 16.08961, 12f, 1_000L,
            WorkerPositionFix.Source.PHONE_GNSS));
        assertTrue(registry.bind(1, "phone-one"));
        assertFalse(registry.snapshot().requiredReliable(FlightMode.FOLLOW, 4_000L));
    }

    @Test public void estimatesBoundedWorkerVelocity() {
        WorkerPositionRegistry registry = new WorkerPositionRegistry();
        registry.update("one", 49d, 14d, 1f, 1_000L, WorkerPositionFix.Source.UWB_FUSED);
        registry.update("one", 49.001d, 14.001d, 1f, 2_000L, WorkerPositionFix.Source.UWB_FUSED);
        registry.bind(1, "one");
        WorkerPositionFix fix = registry.snapshot().primary;
        assertTrue(Math.abs(fix.northMetersPerSecond) <= 4f);
        assertTrue(Math.abs(fix.eastMetersPerSecond) <= 4f);
    }
}
