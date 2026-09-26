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
            "Legacy DJI video mode fallback is missing", errors)
    require("isFlatCameraModeSupported" in dji
            and "setFlatMode(SettingsDefinitions.FlatCameraMode.VIDEO_NORMAL" in dji,
            "Flat Camera Mode is not used for Mini 2 firmware", errors)
    require("startRecordVideo" in dji and "stopRecordVideo" in dji,
            "DJI start/stop recording callbacks are incomplete", errors)
    require("recordingSession.toggle(" in dji and "Kamera právě zpracovává" in dji,
            "Overlapping recording commands are not guarded", errors)
    require("recordingSession.isRecordingOrStarting()" in dji and "photoCommandPending" in dji,
            "FOTO can change the camera mode while recording starts", errors)
    require("recordingSession.cameraState(state.isRecording())" in dji,
            "Unexpected camera interruption is not monitored", errors)
    require("RECORDING_CONFIRMATION_MILLIS" in dji
            and "verifyRecordingStart" in dji
            and "Záznam potvrzen skutečným stavem kamery DJI." in dji,
            "REC is shown as started before the DJI camera confirms recording", errors)
    require("stopRecordVideo" not in (ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightRuntime.java").read_text(encoding="utf-8"),
            "AI flight lifecycle must never stop camera recording", errors)
    require("postCamera(recordingSession.isRecording())" in dji
            and "postCamera(false)" in dji,
            "Camera state is not synchronized from the DJI recording state", errors)
    require("setStorageStateCallBack" in dji and "CameraStorageStatus.evaluate" in dji,
            "Aircraft microSD state is not monitored", errors)
    require("!cameraStorageStatus.ready" in dji,
            "Recording is not blocked when the aircraft microSD card is unavailable", errors)
    require("onCameraStorageState" in session and "onCameraStorageState" in ui,
            "Aircraft microSD status is not exposed in the UI", errors)
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
