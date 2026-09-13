"""Verify the packaged SDK key without writing it or the manifest to logs."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def main():
    key = os.environ.get("DJI_ANDROID_APP_KEY", "").strip()
    if not key or "__" in key or "$(" in key or "placeholder" in key.lower():
        raise SystemExit("A valid DJI_ANDROID_APP_KEY secret is required.")
    sdk = Path(os.environ["ANDROID_HOME"])
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    apk = Path("androidApp/app/build/outputs/apk/dji/debug/app-dji-debug.apk")
    result = subprocess.run([str(analyzer), "manifest", "print", str(apk)], capture_output=True)
    if result.returncode:
        raise SystemExit("Cannot inspect APK manifest.")
    try:
        root = ET.fromstring(result.stdout)
    except ET.ParseError:
        raise SystemExit("Cannot parse APK manifest.") from None
    ns = "{http://schemas.android.com/apk/res/android}"
    values = [node.get(ns + "value") for node in root.findall("application/meta-data")
              if node.get(ns + "name") == "com.dji.sdk.API_KEY"]
    if values != [key]:
        raise SystemExit("Packaged DJI key does not match the saved secret.")
    signers = sorted(sdk.glob("build-tools/*/apksigner"))
    if not signers:
        raise SystemExit("APK signature verifier is unavailable.")
    result = subprocess.run([str(signers[-1]), "verify", str(apk)], capture_output=True)
    if result.returncode:
        raise SystemExit("APK signature verification failed.")
    print("PASS: packaged DJI key matches saved secret; APK signature is valid.")


if __name__ == "__main__":
    main()
