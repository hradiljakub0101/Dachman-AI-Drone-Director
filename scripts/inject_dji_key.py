#!/usr/bin/env python3
"""Modify only the processed build plist, before Xcode signs the bundle."""
import os
import plistlib
import sys
from pathlib import Path

def inject(path, key, require_key):
    key = key.strip()
    valid = bool(key) and not any(s in key.lower() for s in ("__", "$(", "placeholder"))
    if require_key and not valid:
        raise ValueError("DJI_APP_KEY is required for a signed device or Release build")
    with Path(path).open("rb") as stream:
        data = plistlib.load(stream)
    data["DJISDKAppKey"] = key if valid else "__DJI_APP_KEY__"
    with Path(path).open("wb") as stream:
        plistlib.dump(data, stream)

if __name__ == "__main__":
    try:
        destination = Path(os.environ["TARGET_BUILD_DIR"]) / os.environ["INFOPLIST_PATH"]
        signed_device = os.environ.get("PLATFORM_NAME") == "iphoneos" and os.environ.get("CODE_SIGNING_ALLOWED") != "NO"
        inject(destination, os.environ.get("DJI_APP_KEY", ""), signed_device or os.environ.get("CONFIGURATION") == "Release")
        print("DJI configuration processed; key value is never logged.")
    except (KeyError, OSError, ValueError, plistlib.InvalidFileException) as error:
        print("error: DJI configuration failed: " + str(error), file=sys.stderr)
        sys.exit(1)
