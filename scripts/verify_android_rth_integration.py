#!/usr/bin/env python3
"""Static safety guard for verified Home Point and automatic RTH integration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    errors: list[str] = []
    session = (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/DroneSession.java").read_text(encoding="utf-8")
    ui = (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/MainActivity.java").read_text(encoding="utf-8")
    dji = (ROOT / "androidApp/app/src/dji/java/cz/dachman/drone/director/DjiConnection.java").read_text(encoding="utf-8")
    status = (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/ReturnHomeStatus.java").read_text(encoding="utf-8")

    checks = {
        "DroneSession does not expose Home Point preparation": "prepareReturnHome(int heightMeters" in session,
        "DroneSession lacks phone-location Home Point fallback": "prepareReturnHomeFromDevice" in session,
        "UI lacks mandatory take-off point control": "ULOŽIT BOD VZLETU" in ui,
        "UI lacks phone-location Home Point fallback": "HOME Z POLOHY TELEFONU" in ui,
        "UI does not split RTH landing from emergency landing":
            "NÁVRAT A PŘISTÁNÍ" in ui and "NOUZOVĚ PŘISTÁT ZDE" in ui and "setOnLongClickListener" in ui,
        "DJI Home Point is not explicitly stored": "setHomeLocationUsingAircraftCurrentLocation" in dji,
        "DJI Home Point is not read back": "getHomeLocation" in dji,
        "RTH height is not written and verified":
            "setGoHomeHeightInMeters" in dji and "getGoHomeHeightInMeters" in dji,
        "Smart RTH is not enabled and verified":
            "setSmartReturnToHomeEnabled(true" in dji and "getSmartReturnToHomeEnabled" in dji,
        "Connection failsafe is not GO_HOME":
            "setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.GO_HOME" in dji,
        "Smart RTH request is not confirmed": "confirmSmartReturnToHomeRequest(true" in dji,
        "Low battery RTH one-shot latch is missing":
            "lowBatteryRthTriggered" in dji and "AUTOMATIC_RTH_BATTERY_PERCENT" in dji,
        "Take-off does not require the verified return configuration":
            "if (!returnHomeStatus.ready)" in dji,
        "Home Point verification tolerance is missing":
            "MAXIMUM_HOME_VERIFICATION_ERROR_METERS" in status,
    }
    for message, passed in checks.items():
        if not passed:
            errors.append(message)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("Android verified Home Point and automatic RTH integration checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
