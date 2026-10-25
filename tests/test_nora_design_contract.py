"""Static regression contracts for Android-specific design and semantics."""
from pathlib import Path
import unittest

MAIN = Path(__file__).resolve().parents[1] / "native-vpn" / "NoraMainScreen.kt"

class NoraDesignContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = MAIN.read_text(encoding="utf-8")

    def test_real_native_connection_state_remains_source(self):
        self.assertIn("connected = appState.isRunning", self.ui)
        self.assertIn("selectedCountryName = displayedRoute?.countryName", self.ui)
        self.assertIn("painterResource(R.drawable.nora_brand)", self.ui)

    def test_connected_route_is_backed_by_active_native_guid(self):
        # Never render a preferred country as the live VPN endpoint.
        self.assertIn(
            "if (appState.isRunning) activeRoute else preferredRoute", self.ui
        )
        self.assertNotIn("activeRoute ?: preferredRoute", self.ui)
        self.assertIn('"مسیر فعلی نامشخص"', self.ui)

    def test_home_is_responsive_and_not_scrolling(self):
        home = self.ui.split("private fun NoraHomeContent(", 1)[1].split(
            "@Composable\nprivate fun NoraDashboardMetric", 1)[0]
        self.assertIn("BoxWithConstraints(", home)
        self.assertIn("val compact = maxHeight < 665.dp", home)
        self.assertIn("val tiny = maxHeight < 525.dp", home)
        self.assertNotIn("verticalScroll(", home)

    def test_reference_dashboard_uses_real_connection_and_subscription_data(self):
        home = self.ui.split("private fun NoraHomeContent(", 1)[1].split(
            "@Composable\nprivate fun NoraDashboardMetric", 1
        )[0]
        for element in (
            "Connected  |  ", "Selected Location", "Smart Location",
            "Recent Location", "Secure Network", "Remaining Traffic",
            "Subscription", "usage?.remainingTrafficLabel()",
            "usage?.remainingTimeLabel()", "locationPing",
            "onClick = onChooseLocation", "onSelectRecent",
            "val compact = maxHeight < 665.dp",
            "val tiny = maxHeight < 525.dp",
        ):
            self.assertIn(element, home)
        self.assertNotIn("185.251.", home)
        self.assertNotIn("VPN IP:", home)

    def test_teal_dashboard_palette_and_real_power_action(self):
        self.assertIn("private val ink = Color(0xFF041B25)", self.ui)
        self.assertIn("private val cyan = Color(0xFF18B8A9)", self.ui)
        self.assertIn("private val green = Color(0xFF29D8B9)", self.ui)
        self.assertIn("onToggle = startOrStop", self.ui)
        self.assertIn("onAction(MainAction.ToggleService)", self.ui)

    def test_power_button_exposes_accessible_states(self):
        power = self.ui.split("private fun NoraPowerButton(", 1)[1].split(
            "@Composable\nprivate fun NoraSubscriptionGroupCard", 1)[0]
        for term in ('"اتصال NoraProxy"', '"قطع اتصال NoraProxy"',
                     '"اتصال در دسترس نیست"', ".semantics {"):
            self.assertIn(term, power)

    def test_tabs_and_location_cards_have_selectable_semantics(self):
        quick = self.ui.split("private fun NoraQuickCountryCard(", 1)[1].split(
            "@Composable\nprivate fun NoraHeader", 1)[0]
        bottom = self.ui.split("private fun NoraBottomBar(", 1)[1]
        self.assertIn("role = Role.RadioButton", quick)
        self.assertIn("role = Role.Tab", bottom)
        self.assertIn(".heightIn(min = 59.dp)", bottom)
        self.assertIn("contentDescription = null", bottom)
        self.assertIn("Modifier.size(48.dp)", self.ui)
        self.assertIn('"افزودن به علاقه‌مندی‌ها"', self.ui)

if __name__ == "__main__":
    unittest.main()
