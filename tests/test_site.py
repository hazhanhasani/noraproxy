"""Static checks for the public NoraProxy GitHub Pages website."""
import json
import re
import unittest
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"
BASE_URL = "https://hazhanhasani.github.io/noraproxy/"


class SiteParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = set()
        self.links = []
        self.assets = []
        self.errors = []
        self.html_attributes = {}
        self.title_seen = False
        self.in_title = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "html":
            self.html_attributes = a
        if "id" in a:
            if a["id"] in self.ids:
                self.errors.append("Repeated ID: " + a["id"])
            self.ids.add(a["id"])
        if tag == "a":
            href = a.get("href", "")
            self.links.append(href)
            if a.get("target") == "_blank" and not {
                "noopener", "noreferrer"
            }.issubset(set(a.get("rel", "").split())):
                self.errors.append("Unsafe external link: " + href)
        if tag in ("script", "img", "link"):
            url = a.get("src", "") or a.get("href", "")
            if url and url.startswith("./"):
                self.assets.append(url)
        if tag == "img" and "alt" not in a:
            self.errors.append("Missing alt attribute")
        if tag == "title":
            self.in_title = True

    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False

    def handle_data(self, data):
        if self.in_title and data.strip():
            self.title_seen = True


class WebsiteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = (SITE / "index.html").read_text(encoding="utf-8")
        cls.css = (SITE / "styles.css").read_text(encoding="utf-8")
        cls.js = (SITE / "app.js").read_text(encoding="utf-8")
        cls.parser = SiteParser()
        cls.parser.feed(cls.html)

    def test_persian_accessible_semantic_page(self):
        self.assertEqual(self.parser.html_attributes.get("lang"), "fa")
        self.assertEqual(self.parser.html_attributes.get("dir"), "rtl")
        self.assertTrue(self.parser.title_seen)
        self.assertEqual([], self.parser.errors)
        for element in ("main", "hero-title", "features", "steps", "faq", "download"):
            self.assertIn(element, self.parser.ids)

    def test_navigation_points_to_existing_sections(self):
        for link in self.parser.links:
            if link.startswith("#") and link != "#":
                self.assertIn(link[1:], self.parser.ids, link)
            if link.startswith("http"):
                self.assertEqual(urlparse(link).scheme, "https")

    def test_all_local_resources_are_in_repository(self):
        for resource in self.parser.assets:
            if resource == "./assets/nora-icon.jpg":
                path = ROOT / "native-vpn" / "assets" / "nora_icon.jpg"
            else:
                path = SITE / resource.removeprefix("./")
            self.assertTrue(path.is_file(), str(path))

    def test_safe_release_download_fallback(self):
        release = "https://github.com/hazhanhasani/noraproxy/releases/latest"
        self.assertIn(release, self.html)
        self.assertIn("api.github.com/repos/", self.js)
        self.assertIn('"hazhanhasani/noraproxy"', self.js)
        self.assertIn("releases/download/", self.js)
        self.assertIn("safeGithubAsset", self.js)
        self.assertNotIn("eval(", self.js)

    def test_mobile_responsiveness_and_reduced_motion(self):
        self.assertIn("prefers-reduced-motion", self.css)
        self.assertIn("max-width: 640px", self.css)
        self.assertIn(":focus-visible", self.css)
        self.assertIn("aria-expanded", self.html)

    def test_manifest_and_sitemap(self):
        manifest = json.loads((SITE / "manifest.webmanifest").read_text("utf-8"))
        self.assertEqual(manifest["lang"], "fa")
        self.assertEqual(manifest["dir"], "rtl")
        self.assertEqual(manifest["display"], "browser")
        root = ET.parse(SITE / "sitemap.xml").getroot()
        self.assertEqual(
            root.find("{http://www.sitemaps.org/schemas/sitemap/0.9}url/"
                      "{http://www.sitemaps.org/schemas/sitemap/0.9}loc").text,
            BASE_URL,
        )
        self.assertIn(BASE_URL, self.html)

    def test_never_embed_customer_subscription_links(self):
        for p in SITE.glob("*"):
            if p.suffix not in (".html", ".css", ".js", ".xml", ".txt", ".md", ".webmanifest"):
                continue
            content = p.read_text("utf-8")
            self.assertNotRegex(content, r"https?://[^\s'\"]+[?&](?:token|password|uuid)=")


if __name__ == "__main__":
    unittest.main()
