#!/usr/bin/env python3
"""Verify that every Android launcher-icon path resolves to the Dachman artwork."""

from pathlib import Path
import struct
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "androidApp" / "app"
RES = APP / "src" / "main" / "res"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def png_header(path: Path) -> tuple[int, int, int]:
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"Not a PNG: {path}")
    require(data[12:16] == b"IHDR", f"Missing PNG IHDR: {path}")
    width, height, _depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, color_type


manifest = ET.parse(APP / "src" / "main" / "AndroidManifest.xml").getroot()
application = manifest.find("application")
require(application is not None, "Android manifest has no application element")
require(application.get(ANDROID + "icon") == "@mipmap/ic_launcher",
        "Manifest does not use @mipmap/ic_launcher")
require(application.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher_round",
        "Manifest does not use @mipmap/ic_launcher_round")

master = RES / "drawable-nodpi" / "dachman_logo_master.png"
foreground = RES / "drawable-nodpi" / "ic_launcher_foreground.png"
require(png_header(master)[:2] == (1024, 1024), "Master icon must be 1024 x 1024")
fg_width, fg_height, fg_color_type = png_header(foreground)
require((fg_width, fg_height) == (1024, 1024), "Adaptive foreground must be 1024 x 1024")
require(fg_color_type in (4, 6), "Adaptive foreground must contain transparency")

legacy_sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in legacy_sizes.items():
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        path = RES / f"mipmap-{density}" / name
        require(png_header(path)[:2] == (size, size),
                f"Wrong dimensions for {path}: expected {size} x {size}")

for api in ("v26", "v33"):
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = RES / f"mipmap-anydpi-{api}" / name
        adaptive = ET.parse(path).getroot()
        require(adaptive.tag == "adaptive-icon", f"Not an adaptive icon: {path}")
        layers = {child.tag: child.get(ANDROID + "drawable") for child in adaptive}
        require(layers.get("background") == "@color/ic_launcher_background",
                f"Wrong adaptive background: {path}")
        require(layers.get("foreground") == "@drawable/ic_launcher_foreground",
                f"Wrong adaptive foreground: {path}")
        if api == "v33":
            require(layers.get("monochrome") == "@drawable/ic_launcher_foreground",
                    f"Missing monochrome icon: {path}")

source = ROOT / "docs" / "branding" / "dachman-logo-source.jpg"
require(source.is_file() and source.stat().st_size > 0, "Supplied source logo is missing")

print("Android launcher icon integration verified.")
