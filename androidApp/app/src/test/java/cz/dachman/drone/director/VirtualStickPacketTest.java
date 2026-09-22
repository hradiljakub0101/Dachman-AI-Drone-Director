package cz.dachman.drone.director;

import static org.junit.Assert.*;
import org.junit.Test;

public final class VirtualStickPacketTest {
    @Test public void bodyVelocityAxesMatchDjiDocumentation() {
        VirtualStickPacket packet = VirtualStickPacket.from(new FlightCommand(2, -1, 7, .5f, -3));
        assertEquals(-1f, packet.pitch, 0f);
        assertEquals(2f, packet.roll, 0f);
        assertEquals(7f, packet.yaw, 0f);
        assertEquals(.5f, packet.vertical, 0f);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidMotionIsRejected() {
        VirtualStickPacket.from(new FlightCommand(Float.NaN, 0, 0, 0, 0));
    }
}
