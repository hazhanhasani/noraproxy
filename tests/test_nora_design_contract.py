"""Static UI and privacy contracts for the BlueVPN-inspired NoraProxy app.

Build and instrumented device QA are still required. These tests prevent
accidental loss of working VPN callbacks and unsafe remote ad behavior.
"""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "native-vpn" / "NoraMainScreen.kt"
ADS = ROOT / "native-vpn" / "NoraRemoteAds.kt"
MANIFEST = ROOT / "assets" / "ads" / "manifest.json"
PREPARE = ROOT / "scripts" / "prepare_native_vpn.py"


class NoraDesignContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = MAIN.read_text(encoding="utf-8")
        cls.home = cls.ui.split("private fun NoraHomeContent(", 1)[1].split(
            "@Composable\nprivate fun NoraRouteShortcut(", 1
        )[0]
        cls.ads = ADS.read_text(encoding="utf-8")

    def test_native_vpn_service_stays_authoritative(self):
        for expected in (
            "connected = appState.isRunning",
            "if (appState.isRunning) activeRoute else preferredRoute",
            "onToggle = startOrStop",
            "onAction(MainAction.ToggleService)",
            "onAction(MainAction.SelectServer(guid))",
            "selectedCountryName = displayedRoute?.countryName",
            "NoraForegroundSubscriptions.refresh",
            "NoraImportRouter.classify",
        ):
            self.assertIn(expected, self.ui)

    def test_bluevpn_palette_preserves_nora_identity(self):
        for expected in (
            "private val ink = Color(0xFF08090E)",
            "private val surface = Color(0xFF121319)",
            "private val cyan = Color(0xFF4B7DFF)",
            "private val green = Color(0xFF2ED3A1)",
            '"NoraProxy"',
            "painterResource(R.drawable.nora_brand)",
        ):
            self.assertIn(expected, self.ui)

    def test_compact_home_and_real_user_information(self):
        for expected in (
            "BoxWithConstraints(",
            "val compact = maxHeight < 650.dp",
            "val tiny = maxHeight < 535.dp",
            "RoundedCornerShape(52.dp)",
            "NoraPowerButton(",
            "onClick = onToggle",
            "NoraPromotionsStrip()",
            "SystemClock.elapsedRealtime()",
            "usage?.remainingTrafficLabel()",
            "usage?.remainingTimeLabel()",
            "locationPing",
            "onClick = onChooseLocation",
        ):
            self.assertIn(expected, self.home)
        for forbidden in (
            "verticalScroll(", "185.251.", "VPN IP:", "Secure Network",
            "NoraRouteShortcut(", "NoraDashboardMetric(", "loctionFake"
        ):
            self.assertNotIn(forbidden, self.home)

    def test_home_actions_open_real_destinations(self):
        for expected in (
            "onOpenSettings = { tab = NoraTab.Settings }",
            "onOpenSubscription = { tab = NoraTab.Subscription }",
            "onOpenUpdate = { tab = NoraTab.Settings }",
            "chooseRoute(if (smartMode) smartEngine.bestRoute(ranked, null)",
        ):
            self.assertIn(expected, self.ui)

    def test_faster_lists_and_quality_filters(self):
        for expected in (
            "withContext(Dispatchers.IO) {\n            val sorted",
            "NoraRouteSelector.rank(NoraRouteSelector.load(groupId))",
            'contentType = { "location" }',
            'contentType = { "subscription" }',
            "NoraLocationTab.entries.forEach",
            "NoraQualityFilter.entries.toList()",
            '"کیفیت زنده سرورها"',
            "NoraRouteSelector.searchLocations",
            "route.latencyMs in 1L..150L",
            "route.latencyMs in 151L..350L",
        ):
            self.assertIn(expected, self.ui)

    def test_promotion_security_limits_and_pipeline(self):
        for expected in (
            "HttpsURLConnection",
            "MAX_MANIFEST_BYTES",
            "MAX_IMAGE_BYTES",
            "MAX_BANNERS = 4",
            "validFile(",
            "ROOT + item.file",
            "Dispatchers.IO",
            "CACHE_TTL_MS",
            "Bitmap.Config.RGB_565",
            "instanceFollowRedirects = false",
            'uri.scheme.equals("https"',
            "Intent.ACTION_VIEW",
        ):
            self.assertIn(expected, self.ads)
        self.assertNotIn("http://", self.ads)
        self.assertIn('"native-vpn/NoraRemoteAds.kt"', PREPARE.read_text(encoding="utf-8"))
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["version"], 1)
        self.assertIsInstance(manifest["banners"], list)

    def test_accessibility_and_navigation(self):
        power = self.ui.split("private fun NoraPowerButton(", 1)[1].split(
            "@Composable\nprivate fun NoraSubscriptionGroupCard", 1
        )[0]
        bottom = self.ui.split("private fun NoraBottomBar(", 1)[1]
        for term in (
            '"اتصال NoraProxy"', '"قطع اتصال NoraProxy"',
            '"اتصال در دسترس نیست"', ".semantics {",
        ):
            self.assertIn(term, power)
        self.assertIn("role = Role.Tab", bottom)
        self.assertIn(".heightIn(min = 59.dp)", bottom)
        self.assertIn("contentDescription = null", bottom)
        self.assertIn('"افزودن به علاقه‌مندی‌ها"', self.ui)


if __name__ == "__main__":
    unittest.main()
