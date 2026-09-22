package cz.dachman.drone.director;

/** MSDK 4 BODY + VELOCITY: SDK pitch = right, SDK roll = forward (not attitude angles). */
public final class VirtualStickPacket {
    public final float pitch, roll, yaw, vertical;

    private VirtualStickPacket(float pitch, float roll, float yaw, float vertical) {
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.vertical = vertical;
    }

    public static VirtualStickPacket from(FlightCommand command) {
        if (command == null || !Float.isFinite(command.pitch) || !Float.isFinite(command.roll)
                || !Float.isFinite(command.yaw) || !Float.isFinite(command.vertical)
                || !Float.isFinite(command.gimbalPitch)) {
            throw new IllegalArgumentException("Non-finite flight command");
        }
        return new VirtualStickPacket(command.roll, command.pitch, command.yaw, command.vertical);
    }
}
