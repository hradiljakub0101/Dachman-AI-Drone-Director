#!/usr/bin/env python3
"""Source wiring checks complement, but never replace, runtime/SDK/hardware tests."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root / "androidApp/app/src"
dji = (base / "dji/java/cz/dachman/drone/director/DjiConnection.java").read_text()
runtime = (base / "main/java/cz/dachman/drone/director/FlightRuntime.java").read_text()
ui = (base / "main/java/cz/dachman/drone/director/MainActivity.java").read_text()
checks = {
    "production SDK packet uses tested mapping": "VirtualStickPacket.from(command)" in dji
        and "new FlightControlData(packet.pitch, packet.roll, packet.yaw, packet.vertical)" in dji,
    "SDK availability is checked": "current.isVirtualStickControlModeAvailable()" in dji,
    "SDK command outcome reaches runtime": "completion.onComplete(error == null" in dji
        and "session.sendCommand(command, (success, message)" in runtime,
    "ten hertz production loop": "100L, 100L, TimeUnit.MILLISECONDS" in runtime,
    "explicit independent worker choice": "TargetSelection.values()" in ui
        and "if (slot == 1 && tracker.snapshot().secondary == null)" not in ui,
    "live GNSS quality checked": "state.getGPSSignalLevel()" in dji,
    "lost frame cannot be represented as an active target forever": "TARGET_HOLD_AFTER_MILLIS" in ui,
}
failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("Flight-control wiring check failed: " + ", ".join(failed))
print("Flight-control wiring checks passed (not a hardware flight test)")
