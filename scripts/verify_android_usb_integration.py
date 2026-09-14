#!/usr/bin/env python3
"""Fail CI when the DJI USB/AOA hand-off needed by RC-N1 is incomplete."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def main() -> int:
    errors: list[str] = []
    filter_path = ROOT / "androidApp/app/src/dji/res/xml/accessory_filter.xml"
    manifest_path = ROOT / "androidApp/app/src/dji/AndroidManifest.xml"
    activity_path = ROOT / "androidApp/app/src/main/java/cz/dachman/drone/director/MainActivity.java"
    connection_path = ROOT / "androidApp/app/src/dji/java/cz/dachman/drone/director/DjiConnection.java"

    accessory_root = ET.parse(filter_path).getroot()
    models = {
        element.attrib.get("model") or element.attrib.get(ANDROID + "model")
        for element in accessory_root.findall("usb-accessory")
    }
    expected_models = {"T600", "AG410", "com.dji.logiclink", "WM160"}
    require(expected_models <= models, f"Missing DJI USB accessory models: {sorted(expected_models - models)}", errors)

    manifest_root = ET.parse(manifest_path).getroot()
    features = {
        element.attrib.get(ANDROID + "name"): element.attrib.get(ANDROID + "required")
        for element in manifest_root.findall("uses-feature")
    }
    require(features.get("android.hardware.usb.accessory") == "true", "USB accessory feature is missing", errors)
    require(features.get("android.hardware.usb.host") == "false", "Optional USB host feature is missing", errors)

    activity_source = activity_path.read_text(encoding="utf-8")
    connection_source = connection_path.read_text(encoding="utf-8")
    require("onNewIntent(Intent intent)" in activity_source, "MainActivity does not handle a warm USB attach", errors)
    require("ACTION_USB_ACCESSORY_ATTACHED" in activity_source, "MainActivity does not recognize USB accessory intents", errors)
    require("dji.onUsbAccessoryAttached()" in activity_source, "USB intent is not forwarded to DroneSession", errors)
    require("DJISDKManager.USB_ACCESSORY_ATTACHED" in connection_source, "DJI SDK USB broadcast is missing", errors)
    require("started = DJISDKManager.getInstance().startConnectionToProduct()" in connection_source,
            "startConnectionToProduct result is not checked", errors)
    require("connectionRetry" in connection_source, "Automatic DJI connection retry is missing", errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("DJI RC-N1 USB/AOA integration checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
