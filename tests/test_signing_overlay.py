"""Regression tests for safe, reproducible NoraProxy APK signing injection."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "enable_release_signing.py"


class SigningOverlayTests(unittest.TestCase):
    def test_signing_overlay_is_parseable_and_secret_free(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            (root / "scripts").mkdir()
            app = root / "upstream" / "V2rayNG" / "app"
            app.mkdir(parents=True)
            shutil.copy2(SCRIPT, root / "scripts" / SCRIPT.name)
            gradle = app / "build.gradle.kts"
            gradle.write_text(
                'plugins {}\nandroid {\n    buildTypes {\n'
                '        debug {\n        }\n'
                '        release {\n        }\n    }\n}\n',
                encoding="utf-8",
            )
            keystore = root / "test-release.jks"
            keystore.write_bytes(b"test-fixture-keystore-not-a-real-key")
            env = os.environ.copy()
            env.update(
                NORA_SIGNING_STORE_FILE=str(keystore),
                NORA_SIGNING_STORE_PASSWORD="example-store-secret",
                NORA_SIGNING_KEY_ALIAS="example-alias",
                NORA_SIGNING_KEY_PASSWORD="example-key-secret",
            )
            completed = subprocess.run(
                [sys.executable, str(root / "scripts" / SCRIPT.name)],
                cwd=root,
                env=env,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stderr)
            configured = gradle.read_text(encoding="utf-8")
            self.assertIn("import java.util.Properties", configured)
            self.assertIn("import java.util.Base64", configured)
            self.assertIn('signingConfig = signingConfigs.getByName("debug")', configured)
            self.assertIn('signingConfig = signingConfigs.getByName("noraRelease")', configured)
            self.assertNotIn("example-store-secret", configured)
            self.assertNotIn("example-key-secret", configured)
            self.assertNotIn("example-alias", configured)
            credentials = keystore.with_suffix(".signing.properties")
            self.assertTrue(credentials.is_file())
            self.assertEqual(credentials.stat().st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
