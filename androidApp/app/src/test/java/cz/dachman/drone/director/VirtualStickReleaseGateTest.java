package cz.dachman.drone.director;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class VirtualStickReleaseGateTest {
    @Test public void returnHomeWaitsForInFlightAiShutdown() {
        VirtualStickReleaseGate gate = new VirtualStickReleaseGate();
        AtomicInteger shutdowns = new AtomicInteger();
        AtomicReference<DroneSession.Completion> sdkReply = new AtomicReference<>();
        AtomicInteger startedRth = new AtomicInteger();
        VirtualStickReleaseGate.Release shutdown = reply -> {
            shutdowns.incrementAndGet();
            sdkReply.set(reply);
        };
        gate.request(shutdown, (success, message) -> {});
        gate.request(shutdown, (success, message) -> {
            if (success) startedRth.incrementAndGet();
        });
        assertTrue(gate.pending());
        assertEquals(1, shutdowns.get());
        assertEquals(0, startedRth.get());
        sdkReply.get().onComplete(true, "Virtual Stick vypnut.");
        assertFalse(gate.pending());
        assertEquals(1, startedRth.get());
    }

    @Test public void rejectedShutdownNeverStartsReturnHome() {
        VirtualStickReleaseGate gate = new VirtualStickReleaseGate();
        AtomicReference<DroneSession.Completion> sdkReply = new AtomicReference<>();
        AtomicInteger startedRth = new AtomicInteger();
        gate.request(sdkReply::set, (success, message) -> {});
        gate.request(sdkReply::set, (success, message) -> {
            if (success) startedRth.incrementAndGet();
        });
        sdkReply.get().onComplete(false, "DJI nepovolilo vypnutí.");
        assertEquals(0, startedRth.get());
    }

    @Test public void disconnectRejectsWaitingReturnAndIgnoresLateCallback() {
        VirtualStickReleaseGate gate = new VirtualStickReleaseGate();
        AtomicReference<DroneSession.Completion> sdkReply = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        gate.request(sdkReply::set, (success, message) -> calls.incrementAndGet());
        gate.cancel("Spojení ztraceno.");
        sdkReply.get().onComplete(true, "Pozdní odpověď.");
        assertEquals(1, calls.get());
    }
}
