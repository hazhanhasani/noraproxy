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

    def test_home_is_responsive_and_not_scrolling(self):
        home = self.ui.split("private fun NoraHomeContent(", 1)[1].split(
            "@Composable\nprivate fun NoraDashboardMetric", 1)[0]
        self.assertIn("BoxWithConstraints(", home)
        self.assertIn("val compact = maxHeight < 665.dp", home)
        self.assertIn("val tiny = maxHeight < 525.dp", home)
        self.assertNotIn("verticalScroll(", home)

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
