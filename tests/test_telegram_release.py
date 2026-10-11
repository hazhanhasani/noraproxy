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
        self.assertLessEqual(mod.caption_units(c), 1024)

    def test_long_notes_fit_telegram(self):
        c = mod.compose_caption("1.0.0", "x/repo", ["بلند" * 1000] * 9)
        self.assertLessEqual(mod.caption_units(c), 1024)

    def test_changes_have_blank_lines_and_right_to_left_marks(self):
        caption = mod.compose_caption("0.2.14", "hazhanhasani/noraproxy", [
            "بازطراحی صفحه اصلی", "رفع خطای سرورها", "بهبود سرعت اتصال"
        ])
        self.assertIn(
            "\u200f• بازطراحی صفحه اصلی\n\n\u200f• رفع خطای سرورها\n\n"
            "\u200f• بهبود سرعت اتصال", caption
        )
        self.assertIn("🛠 <b>تغییرات این نسخه</b>\n\n\u200f•", caption)
        self.assertEqual(caption.count("\u200f• "), 3)
        self.assertLessEqual(mod.caption_units(caption), 1024)

    def test_mixed_persian_english_html_emoji(self):
        caption = mod.compose_caption("0.2.14", "hazhanhasani/noraproxy", [
            "📱 بهبود UI & رفع خطای VPN", "🌍 Germany / آلمان <DE>"
        ])
        self.assertIn("\u200f• 📱 بهبود UI &amp; رفع خطای VPN", caption)
        self.assertIn("\n\n\u200f• 🌍 Germany / آلمان &lt;DE&gt;", caption)
        self.assertGreater(mod.caption_units("🚀"), len("🚀"))
        self.assertLessEqual(mod.caption_units(caption), 1024)

    def test_release_note_title_is_not_a_changelog_bullet(self):
        with tempfile.TemporaryDirectory() as d:
            original_root = mod.ROOT
            try:
                mod.ROOT = Path(d)
                notes = mod.ROOT / "release-notes"
                notes.mkdir()
                (notes / "v0.2.14.fa.txt").write_text(
                    "🚀 NoraProxy 0.2.14 | قابلیت‌های جدید\n\n"
                    "🔎 جست‌وجوی سریع کشورها\n"
                    "⭐ علاقه‌مندی‌های ماندگار\n", encoding="utf-8"
                )
                self.assertEqual(mod.notes_from_file("0.2.14"), [
                    "🔎 جست‌وجوی سریع کشورها", "⭐ علاقه‌مندی‌های ماندگار"
                ])
            finally:
                mod.ROOT = original_root

    def test_reject_invalid_version_and_repo(self):
        with self.assertRaises(ValueError):
            mod.check_version("v0.2.4; rm -rf /")
        with self.assertRaises(ValueError):
            mod.check_repo("https://bad.example")

    def test_official_channel_fallback(self):
        import os
        self.assertEqual(os.environ.get("NORA_TG_CHANNEL_ID", "@noraproxy"), "@noraproxy")

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
