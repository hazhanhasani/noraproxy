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
            "NoraSwipeConnect(",
            "onToggle = onToggle",
            "NoraPromotionsStrip()",
            '.verticalScroll(rememberScrollState())',
            'Text("NoraProxy", color = light,',
            "SystemClock.elapsedRealtime()",
            "usage?.remainingTrafficLabel()",
            "usage?.remainingTimeLabel()",
            "locationPing",
            "onClick = onChooseLocation",
        ):
            self.assertIn(expected, self.home)
        for forbidden in (
            "185.251.", "VPN IP:", "Secure Network",
            "NoraRouteShortcut(", "NoraDashboardMetric(", "loctionFake",
            "modifier = Modifier.fillMaxWidth().weight(1f)"
        ):
            self.assertNotIn(forbidden, self.home)

    def test_swipe_is_directional_and_accessible(self):
        self.assertIn("detectHorizontalDragGestures(", self.ui)
        self.assertIn("dragPx - amount", self.ui)
        self.assertIn("dragPx >= travelPx * .70f", self.ui)
        self.assertIn('onClick(label = if (connected)', self.ui)
        self.assertIn("NoraFlagBadge(", self.ui)
        self.assertIn("nora_tab_subscription", self.ui)

    def test_ad_banner_fills_available_card_width(self):
        self.assertIn("Modifier.fillMaxWidth().aspectRatio(3.75f)", self.ads)
        self.assertIn("contentScale = ContentScale.Crop", self.ads)
        self.assertIn("Modifier.align(Alignment.BottomEnd)", self.ads)
        self.assertNotIn("Modifier.fillMaxWidth().height(81.dp)", self.ads)
        self.assertNotIn("contentScale = ContentScale.Fit", self.ads)

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

    def test_subscription_rows_do_not_decode_profiles_during_draw(self):
        self.assertIn("var groupNodeCounts by remember", self.ui)
        self.assertIn("group.guid to runCatching {", self.ui)
        self.assertIn("MmkvManager.decodeServerList(group.guid).size", self.ui)
        self.assertIn("numberOfNodes = groupNodeCounts[group.guid] ?: 0", self.ui)
        self.assertNotIn(
            "numberOfNodes = MmkvManager.decodeServerList(group.guid).size",
            self.ui
        )
        self.assertIn("var groupSwitchBusy by remember", self.ui)
        self.assertIn("catch (cancelled: kotlinx.coroutines.CancellationException)", self.ui)
        self.assertIn("withContext(Dispatchers.IO) {\n                                                        NoraRouteSelector.rank(", self.ui)

    def test_banner_network_failures_are_guarded_and_images_refresh(self):
        self.assertIn("val connection = try {", self.ads)
        self.assertIn("} catch (_: Exception) {\n            return null", self.ads)
        self.assertIn("val revisionChanged = item.revision != null", self.ads)
        self.assertIn("val url = ROOT + item.file + (item.revision?", self.ads)
        self.assertIn('safeRevision = Regex("^[a-fA-F0-9]{40}$")', self.ads)
        self.assertIn("manifest.json?refresh=", self.ads)
        self.assertIn("delay(15_000L)", self.ads)
        self.assertIn("DisposableEffect(lifecycleOwner)", self.ads)
        self.assertIn("val loadedRevision: String?", self.ads)
        self.assertIn('it.id + ":" + (it.loadedRevision ?: "uncached")', self.ads)
        self.assertIn("val loadedRevision = runCatching { revisionFile.readText() }", self.ads)
        self.assertIn("Lifecycle.State.STARTED", self.ads)
        self.assertIn("rotationSeconds(context: Context): Int", self.ads)
        self.assertIn('optInt("rotationSeconds", 8).coerceIn(3, 30)', self.ads)
        self.assertIn("delay(rotationSeconds * 1000L)", self.ads)
        self.assertIn("instanceFollowRedirects = false", self.ads)
        self.assertIn("local.length() in 1L..MAX_IMAGE_BYTES.toLong()", self.ads)

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
