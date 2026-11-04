"""Regression tests for GitHub ad auto-discovery; no Android toolchain required."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.sync_nora_ads import synchronize


class NoraBannerSyncTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.ads = self.root / "assets" / "ads"
        self.ads.mkdir(parents=True)
        self.manifest = self.ads / "manifest.json"
        self.manifest.write_text(json.dumps({
            "version": 1, "enabled": True, "rotationSeconds": 8,
            "banners": [{"id": "banner-1", "file": "banner-1.webp", "enabled": True}]
        }), encoding="utf-8")

    def current(self):
        return json.loads(self.manifest.read_text(encoding="utf-8"))

    def test_uploaded_png_replaces_missing_placeholder(self):
        (self.ads / "file_001.png").write_bytes(b"good-image-data")
        self.assertTrue(synchronize(self.root))
        self.assertEqual([b["file"] for b in self.current()["banners"]], ["file_001.png"])
        self.assertFalse(synchronize(self.root))

    def test_preserves_click_target_and_title(self):
        (self.ads / "sale.webp").write_bytes(b"webp")
        doc = self.current()
        doc["banners"] = [{
            "id": "sale", "file": "sale.webp", "title": "پیشنهاد",
            "enabled": True, "target": "https://example.com"
        }]
        self.manifest.write_text(json.dumps(doc), encoding="utf-8")
        synchronize(self.root)
        self.assertEqual(self.current()["banners"][0]["target"], "https://example.com")
        self.assertEqual(self.current()["banners"][0]["title"], "پیشنهاد")

    def test_ignores_oversized_and_unsafe_filenames(self):
        (self.ads / "huge.png").write_bytes(b"x" * (2 * 1024 * 1024 + 1))
        (self.ads / "unsafe..name.png").write_bytes(b"x")
        (self.ads / "test.svg").write_text("<svg/>", encoding="utf-8")
        synchronize(self.root)
        self.assertEqual(self.current()["banners"], [])

    def test_limits_to_four_images(self):
        for i in range(6):
            (self.ads / f"image_{i}.png").write_bytes(b"data")
        with patch("scripts.sync_nora_ads.image_timestamp", side_effect=lambda _r,p: int(p.stem[-1])):
            synchronize(self.root)
        self.assertEqual(len(self.current()["banners"]), 4)
        self.assertEqual(self.current()["banners"][0]["file"], "image_5.png")

    def test_rejects_insecure_click_targets(self):
        (self.ads / "ok.png").write_bytes(b"data")
        doc = self.current()
        doc["banners"] = [{
            "id": "ok", "file": "ok.png", "target": "javascript:alert(1)"
        }]
        self.manifest.write_text(json.dumps(doc), encoding="utf-8")
        synchronize(self.root)
        self.assertNotIn("target", self.current()["banners"][0])


if __name__ == "__main__":
    unittest.main()
