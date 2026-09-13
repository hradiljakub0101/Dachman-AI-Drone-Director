import plistlib
import tempfile
import unittest
from pathlib import Path
from inject_dji_key import inject

class InjectionTests(unittest.TestCase):
    def test_missing_key_preserves_plist(self):
        for key in ("", "__DJI_APP_KEY__", "$(DJI_APP_KEY)", "placeholder"):
            with tempfile.TemporaryDirectory() as root:
                path = Path(root) / "Info.plist"
                original = plistlib.dumps({"CFBundleIdentifier": "test", "DJISDKAppKey": "original"})
                path.write_bytes(original)
                with self.assertRaises(ValueError): inject(path, key, True)
                self.assertEqual(path.read_bytes(), original)

    def test_injects_sdk_field_preserving_bundle_identity(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Info.plist"
            path.write_bytes(plistlib.dumps({"CFBundleIdentifier": "test"}))
            inject(path, " test-key ", True)
            self.assertEqual(plistlib.loads(path.read_bytes()), {"CFBundleIdentifier": "test", "DJISDKAppKey": "test-key"})

    def test_debug_can_build_without_live_key(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Info.plist"
            path.write_bytes(plistlib.dumps({}))
            inject(path, "", False)
            self.assertEqual(plistlib.loads(path.read_bytes())["DJISDKAppKey"], "__DJI_APP_KEY__")

if __name__ == "__main__": unittest.main()
