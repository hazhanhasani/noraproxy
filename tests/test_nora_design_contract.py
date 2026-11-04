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
ADVANCED = ROOT / "native-vpn" / "NoraAdvancedScreen.kt"


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

    def test_phase_two_advanced_ui_uses_real_xray_settings(self):
        advanced = ADVANCED.read_text(encoding="utf-8")
        prepare = PREPARE.read_text(encoding="utf-8")
        for expected in (
            "NoraAdvancedScreen(",
            "NoraAdvancedPage.Apps",
            "NoraAdvancedPage.Routing",
            "NoraAdvancedPage.Network",
            "advancedPage = null",
            "onNavigate = onNavigate",
        ):
            self.assertIn(expected, self.ui)
        self.assertIn("native-vpn/NoraAdvancedScreen.kt", prepare)
        for expected in (
            "AppManagerUtil.loadNetworkAppList",
            "AppConfig.PREF_PER_APP_PROXY_SET",
            "AppConfig.PREF_PER_APP_PROXY",
            "AppConfig.PREF_BYPASS_APPS",
            "SettingsManager.saveRoutingRuleset",
            "AppConfig.PREF_ROUTING_DOMAIN_STRATEGY",
            "AppConfig.PREF_LOCAL_DNS_ENABLED",
            "AppConfig.PREF_IPV6_ENABLED",
            "SettingsChangeManager.makeRestartService",
            "SettingsChangeManager.notifySettingChanged",
            "BackHandler(onBack = onBack)",
        ):
            self.assertIn(expected, advanced)
        self.assertNotIn("https://github.com/hiddify/", advanced)
        self.assertNotIn("CoreVpnService", advanced)
        self.assertIn("'versionCode = 228'", prepare)
        self.assertIn('"0.2.28"', prepare)

    def test_phase_two_rule_and_dns_editors_are_packaged(self):
        prepare = PREPARE.read_text(encoding="utf-8")
        advanced = ADVANCED.read_text(encoding="utf-8")
        editor = (ROOT / "native-vpn" / "NoraAdvancedEditors.kt").read_text(encoding="utf-8")
        validator = (ROOT / "native-vpn" / "NoraAdvancedValidation.kt").read_text(encoding="utf-8")
        for name in ("NoraAdvancedEditors.kt", "NoraAdvancedValidation.kt",
                     "NoraAdvancedValidationTest.kt"):
            self.assertIn(name, prepare)
        for token in ("NoraRoutingRuleEditor(", "NoraDnsServersEditor(",
                      "SettingsManager.removeRoutingRuleset(",
                      "MmkvManager.encodeRoutingRulesets(updated)",
                      "AppConfig.PREF_REMOTE_DNS", "AppConfig.PREF_VPN_DNS"):
            self.assertIn(token, advanced)
        for token in ("validateRule(", "validateDnsCsv(", "onSave(", "confirmDelete"):
            self.assertIn(token, editor)
        for token in ("UUID.randomUUID()", "validatePorts(", "validateDnsCsv("):
            self.assertIn(token, validator)

    def test_theme_and_diagnostics_are_native_and_safe(self):
        prepare = PREPARE.read_text(encoding="utf-8")
        palette = (ROOT / "native-vpn" / "NoraTheme.kt").read_text(encoding="utf-8")
        diagnostics = (ROOT / "native-vpn" / "NoraDiagnostics.kt").read_text(encoding="utf-8")
        advanced = ADVANCED.read_text(encoding="utf-8")

        for name in ("NoraTheme.kt", "NoraDiagnostics.kt", "NoraThemeTest.kt",
                     "NoraDiagnosticReaderTest.kt"):
            self.assertIn(name, prepare)
        for required in ("NoraThemeMode.entries", "ThemeManager.setThemeMode(",
                         "LocalNoraPalette provides noraPalette", "noraPalette.materialColors()",
                         "NoraAdvancedPage.Logs", "NoraAdvancedPage.Backup"):
            self.assertIn(required, self.ui)
        for required in ("NoraThemeMode", "NoraPalettes", "LocalNoraPalette",
                         "Light = NoraPalette(", "Dark = NoraPalette("):
            self.assertIn(required, palette)
        for required in ("NoraLogsScreen", "NoraBackupHubScreen", "NoraDiagnosticReader",
                         "REDACTED-URL", "REDACTED-CONFIG", "ProcessBuilder(",
                         "Dispatchers.IO", "read()"):
            self.assertIn(required, diagnostics)
        self.assertIn("onNavigate(MainDestination.BackupRestore)", advanced)
        self.assertIn("onNavigate(MainDestination.Logcat)", advanced)
        self.assertNotIn("Intent.ACTION_SEND", diagnostics)

    def test_phase_three_import_export_and_mtu_safety(self):
        prepare = PREPARE.read_text(encoding="utf-8")
        advanced = ADVANCED.read_text(encoding="utf-8")
        transfer = (ROOT / "native-vpn" / "NoraRoutingTransfer.kt").read_text(encoding="utf-8")
        editor = (ROOT / "native-vpn" / "NoraAdvancedEditors.kt").read_text(encoding="utf-8")
        validation = (ROOT / "native-vpn" / "NoraAdvancedValidation.kt").read_text(encoding="utf-8")

        for expected in ("NoraRoutingTransfer.kt", "NoraRoutingTransferTest.kt"):
            self.assertIn(expected, prepare)
        for expected in ("NoraRoutingTransfer.preview(", "NoraRoutingTransfer.exportUnlocked(",
                         "NoraRoutingTransfer.replaceUnlocked(", "importBaseline",
                         "MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_RULESET",
                         "NoraVpnMtuEditor(", "AppConfig.PREF_VPN_MTU"):
            self.assertIn(expected, advanced)
        for expected in ("MAX_CLIPBOARD_CHARS", "locked == true",
                         "UUID.randomUUID()", "fun exportUnlocked("):
            self.assertIn(expected, transfer)
        self.assertIn("fun validateVpnMtu(", validation)
        self.assertIn("NoraAdvancedValidation.validateVpnMtu(value)", editor)

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
