"""Regression contracts for the NoraProxy BlueVPN-inspired Android UI.

These checks guard source-of-truth semantics and interaction wiring; device
rendering, accessibility, and Gradle compilation are verified separately.
"""
from pathlib import Path
import unittest

MAIN = Path(__file__).resolve().parents[1] / "native-vpn" / "NoraMainScreen.kt"


class NoraDesignContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = MAIN.read_text(encoding="utf-8")
        cls.home = cls.ui.split("private fun NoraHomeContent(", 1)[1].split(
            "@Composable\nprivate fun NoraRouteShortcut(", 1
        )[0]

    def test_native_service_stays_authoritative(self):
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
        self.assertNotIn("activeRoute ?: preferredRoute", self.ui)

    def test_bluevpn_palette_keeps_nora_branding(self):
        for expected in (
            "private val ink = Color(0xFF08090E)",
            "private val surface = Color(0xFF121319)",
            "private val cyan = Color(0xFF4B7DFF)",
            "private val green = Color(0xFF2ED3A1)",
            '"NoraProxy"',
            "painterResource(R.drawable.nora_brand)",
        ):
            self.assertIn(expected, self.ui)

    def test_capsule_connection_screen_is_responsive_and_honest(self):
        for expected in (
            "BoxWithConstraints(",
            "val compact = maxHeight < 650.dp",
            "val tiny = maxHeight < 535.dp",
            ".weight(1f)",
            "RoundedCornerShape(56.dp)",
            "NoraPowerButton(",
            "onClick = onToggle",
            '"زمان مشاهده اتصال"',
            "SystemClock.elapsedRealtime()",
            "usage?.remainingTrafficLabel()",
            "usage?.remainingTimeLabel()",
            "locationPing",
            "onClick = onChooseLocation",
        ):
            self.assertIn(expected, self.home)
        for forbidden in ("verticalScroll(", "185.251.", "VPN IP:", "Secure Network"):
            self.assertNotIn(forbidden, self.home)

    def test_all_home_shortcuts_open_real_destinations(self):
        for expected in (
            "onOpenSettings = { tab = NoraTab.Settings }",
            "onOpenSubscription = { tab = NoraTab.Subscription }",
            "onOpenUpdate = { tab = NoraTab.Settings }",
            "onSelectSmart",
            "onSelectRecent",
        ):
            self.assertIn(expected, self.ui)

    def test_bluevpn_location_tabs_and_real_quality_filters(self):
        for expected in (
            "NoraLocationTab.entries.forEach",
            "NoraQualityFilter.entries.toList()",
            '"همه"), Favorites("علاقه‌مندی"), Recent("اخیر"',
            '"کیفیت زنده سرورها"',
            "nodes.count { it.latencyMs > 0L }",
            "onAction(MainAction.TestRealAllServers)",
            "NoraRouteSelector.searchLocations",
            "route.latencyMs in 1L..150L",
            "route.latencyMs in 151L..350L",
        ):
            self.assertIn(expected, self.ui)

    def test_accessibility_and_bottom_navigation(self):
        power = self.ui.split("private fun NoraPowerButton(", 1)[1].split(
            "@Composable\nprivate fun NoraSubscriptionGroupCard", 1
        )[0]
        bottom = self.ui.split("private fun NoraBottomBar(", 1)[1]
        for term in ('"اتصال NoraProxy"', '"قطع اتصال NoraProxy"',
                     '"اتصال در دسترس نیست"', ".semantics {"):
            self.assertIn(term, power)
        self.assertIn("role = Role.Tab", bottom)
        self.assertIn(".heightIn(min = 59.dp)", bottom)
        self.assertIn("contentDescription = null", bottom)
        self.assertIn('"افزودن به علاقه‌مندی‌ها"', self.ui)


if __name__ == "__main__":
    unittest.main()
