#!/usr/bin/env python3
"""Static guard for the camera recording path exposed by the Android UI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def require(ok: bool, message: str, errors: list[str]) -> None:
    if not ok:
        errors.append(message)

def main() -> int:
    errors: list[str] = []
    ui = (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/MainActivity.java").read_text(encoding="utf-8")
    session = (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/DroneSession.java").read_text(encoding="utf-8")
    dji = (ROOT / "androidApp/app/src/dji/java/cz/dachman/drone/director/DjiConnection.java").read_text(encoding="utf-8")
    demo = (ROOT / "androidApp/app/src/demo/java/cz/dachman/drone/director/DjiConnection.java").read_text(encoding="utf-8")

    require("recordButton = button" in ui and "dji.toggleRecording" in ui,
            "REC button is not wired to DroneSession.toggleRecording", errors)
    require("onCameraState(boolean recording)" in session and "onCameraState(boolean recording)" in ui,
            "Camera recording state is not exposed to and rendered by the UI", errors)
    require("toggleRecording(Completion completion)" in dji, "DJI recording entry point is missing", errors)
    require("setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO" in dji,
            "DJI video mode is not selected before recording", errors)
    require("startRecordVideo" in dji and "stopRecordVideo" in dji,
            "DJI start/stop recording callbacks are incomplete", errors)
    require("recordingCommandPending" in dji and "Kamera právě zpracovává" in dji,
            "Overlapping recording commands are not guarded", errors)
    require("postCamera(true)" in dji and "postCamera(false)" in dji,
            "Successful start/stop does not immediately synchronize UI state", errors)
    require("toggleRecording(Completion completion)" in demo,
            "Demo flavor does not implement the recording contract", errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("Android camera recording integration checks passed")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
