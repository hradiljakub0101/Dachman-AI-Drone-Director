package cz.dachman.drone.director;

import org.junit.Test;
import static org.junit.Assert.*;

public class ControlAuthorityTest {
    @Test public void confirmingAnotherTargetDoesNotDowngradeActiveControl() {
        ControlAuthority authority = new ControlAuthority();
        authority.targetConfirmed(true);
        authority.activateAi();
        authority.targetConfirmed(true);
        assertEquals(ControlAuthority.State.AI_ACTIVE, authority.state());
    }
    @Test public void targetConfirmationPreparesAndApprovalActivatesAi() {
        ControlAuthority authority = new ControlAuthority();
        assertTrue(authority.targetConfirmed(true));
        assertEquals(ControlAuthority.State.AI_READY, authority.state());
        assertTrue(authority.activateAi());
        assertEquals(ControlAuthority.State.AI_ACTIVE, authority.state());
    }

    @Test public void rcTakeoverCannotBeClearedByDetectionOrAutomaticActivation() {
        ControlAuthority authority = new ControlAuthority();
        assertTrue(authority.targetConfirmed(true));
        assertTrue(authority.activateAi());
        authority.pilotTookOver();

        assertEquals(ControlAuthority.State.PILOT_TAKEOVER, authority.state());
        assertTrue(authority.manualRearmRequired());
        assertFalse(authority.targetConfirmed(true));
        assertFalse(authority.activateAi());
        assertEquals(ControlAuthority.State.PILOT_TAKEOVER, authority.state());
    }

    @Test public void rcTakeoverNeedsExplicitRearmAndAnotherActivation() {
        ControlAuthority authority = new ControlAuthority();
        authority.targetConfirmed(true);
        authority.activateAi();
        authority.pilotTookOver();

        assertTrue(authority.confirmManualRearm(true));
        assertFalse(authority.manualRearmRequired());
        assertEquals(ControlAuthority.State.AI_READY, authority.state());
        assertTrue(authority.activateAi());
    }

    @Test public void rearmFailsWithoutConfirmedWorker() {
        ControlAuthority authority = new ControlAuthority();
        authority.pilotTookOver();
        assertFalse(authority.confirmManualRearm(false));
        assertEquals(ControlAuthority.State.PILOT_TAKEOVER, authority.state());
    }

    @Test public void rthHasDedicatedVisibleAuthorityState() {
        ControlAuthority authority = new ControlAuthority();
        assertTrue(authority.beginRth(true));
        assertEquals(ControlAuthority.State.RTH, authority.state());
        authority.finishRth();
        assertEquals(ControlAuthority.State.MANUAL, authority.state());
    }
}
