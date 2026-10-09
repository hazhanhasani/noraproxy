import importlib.util
from pathlib import Path
import tempfile
import unittest

FILE = Path(__file__).resolve().parents[1] / "scripts" / "telegram_release.py"
spec = importlib.util.spec_from_file_location("nora_telegram", FILE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class NoraTelegramReleaseTests(unittest.TestCase):
    def test_caption_is_professional_safe_and_bounded(self):
        c = mod.compose_caption("0.2.4", "hazhanhasani/noraproxy", [
            "رفع اختلال & بهبود اتصال",
            "بهبود <UI> و نمایش لوگو"
        ])
        self.assertIn("<b>NoraProxy | v0.2.4</b>", c)
        self.assertIn("&amp;", c)
        self.assertIn("&lt;UI&gt;", c)
        self.assertIn("SHA-256", c)
        self.assertIn("releases/tag/v0.2.4", c)
        self.assertLessEqual(len(mod.html.unescape(mod.re.sub(r"<[^>]*>", "", c))), 1024)

    def test_long_notes_fit_telegram(self):
        c = mod.compose_caption("1.0.0", "x/repo", ["بلند" * 1000] * 9)
        self.assertLessEqual(len(mod.html.unescape(mod.re.sub(r"<[^>]*>", "", c))), 1024)

    def test_reject_invalid_version_and_repo(self):
        with self.assertRaises(ValueError):
            mod.check_version("v0.2.4; rm -rf /")
        with self.assertRaises(ValueError):
            mod.check_repo("https://bad.example")

    def test_checksum_verification_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "NoraProxy-0.2.4.apk"
            apk.write_bytes(b"x" * 1_100_000)
            checksum = Path(d) / (apk.name + ".sha256")
            checksum.write_text("0" * 64 + "  " + apk.name + "\n")
            with self.assertRaises(ValueError):
                mod.verified_apk(apk, checksum, "0.2.4")

if __name__ == "__main__":
    unittest.main()
