package cz.dachman.drone.director;
import org.junit.Test;
import static org.junit.Assert.*;
public class ApprovalGateTest {
    @Test public void deniedAuthenticationCannotApprove() {
        ApprovalGate gate = new ApprovalGate(); long token = gate.request("orbit");
        assertFalse(gate.approve(token, false)); assertEquals("orbit", gate.pending());
    }
    @Test public void cancellationRejectsLateBiometricCallback() {
        ApprovalGate gate = new ApprovalGate(); long token = gate.request("orbit");
        gate.cancel(); assertFalse(gate.approve(token, true));
    }
    @Test public void changingManeuverInvalidatesPreviousChallenge() {
        ApprovalGate gate = new ApprovalGate(); long old = gate.request("orbit");
        long current = gate.request("follow"); assertFalse(gate.approve(old, true)); assertTrue(gate.approve(current, true));
    }
    @Test public void approvalIsSingleUse() {
        ApprovalGate gate = new ApprovalGate(); long token = gate.request("orbit");
        assertTrue(gate.approve(token, true)); assertFalse(gate.approve(token, true));
    }
}
