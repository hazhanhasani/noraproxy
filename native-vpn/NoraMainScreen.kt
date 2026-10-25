package com.v2ray.ang.ui.main

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// NoraProxy's cyan circuitry and deep navy, rather than generic VPN green.
// NoraProxy VNext / semantic tokens shared with the editable Figma design.
private val ink = Color(0xFF041B25)
private val surface = Color(0xFF102C39)
private val surface2 = Color(0xFF1C3B47)
private val cyan = Color(0xFF18B8A9)
private val blue = Color(0xFF66D9C8)
private val light = Color(0xFFF1FCFC)
private val muted = Color(0xFFA5BEC2)
private val green = Color(0xFF29D8B9)
private val amber = Color(0xFFFFCE88)
private val edge = Color(0xFF345862)
private val corner = RoundedCornerShape(24.dp)

private enum class NoraTab(val title: String, val iconRes: Int) {
    Home("خانه", R.drawable.nora_tab_home),
    Locations("لوکیشن‌ها", R.drawable.nora_tab_locations),
    Subscription("اشتراک", R.drawable.nora_tab_subscription),
    Settings("تنظیمات", R.drawable.nora_tab_settings)
}

/**
 * The customer only sees locations. The original v2rayNG runtime owns VPN
 * permission, service lifecycle, profile storage and Xray connectivity.
 */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val context = LocalContext.current
    val tapsell = remember(context) { NoraTapsellAds(context.noraActivity()) }
    val adShowing by tapsell.blocking.collectAsStateWithLifecycle()
    DisposableEffect(tapsell) {
        tapsell.start()
        onDispose { tapsell.dispose() }
    }
    val appState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val loading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val updater = remember(context.applicationContext) { NoraUpdater(context.applicationContext) }
    val updateState by updater.state.collectAsStateWithLifecycle()
    DisposableEffect(updater) { onDispose { updater.dispose() } }
    LaunchedEffect(updater) { updater.check() }
    var tab by remember { mutableStateOf(NoraTab.Home) }
    var subscription by remember { mutableStateOf("") }
    var importExpanded by remember { mutableStateOf(true) }
    var editorVisible by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<SubscriptionCache?>(null) }
    var deletingGroup by remember { mutableStateOf<SubscriptionCache?>(null) }
    var groupBusy by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()
    val smartPreferences = remember(context) {
        context.getSharedPreferences("nora_smart_engine", android.content.Context.MODE_PRIVATE)
    }
    var smartMode by remember { mutableStateOf(smartPreferences.getBoolean("smart_mode", true)) }
    var automaticRecovery by remember {
        mutableStateOf(smartPreferences.getBoolean("auto_recovery", true))
    }
    val smartEngine = remember { NoraSmartEngine() }
    var appVisible by remember { mutableStateOf(false) }
    var networkGeneration by remember { mutableIntStateOf(0) }
    var lastHandledNetworkGeneration by remember { mutableIntStateOf(0) }
    var fullProbeStartedGroup by remember { mutableStateOf<String?>(null) }
    val pingPrefs = remember(context) { context.getSharedPreferences("nora_ui", android.content.Context.MODE_PRIVATE) }
    var livePingEnabled by remember { mutableStateOf(pingPrefs.getBoolean("live_ping", true)) }
    val syncingGroups by NoraForegroundSubscriptions.running.collectAsStateWithLifecycle()
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importScope.launch {
                val payload = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                        val sample = ((maxOf(bounds.outWidth, bounds.outHeight) + 1599) / 1600).coerceAtLeast(1)
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bitmap = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                        bitmap?.let { QRCodeDecoder.syncDecodeQRCode(it).also { _ -> it.recycle() } }
                    }.getOrNull()
                }
                if (!payload.isNullOrBlank() && payload.length <= 65536) {
                    onAction(MainAction.ImportBatchConfig(payload))
                } else {
                    Toast.makeText(context, "کد QR معتبر در تصویر پیدا نشد", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Keep the user's preferred country across app restarts, while the real
    // selected server GUID remains managed by the VPN backend.
    val routePreferences = remember(context) {
        context.getSharedPreferences("nora_route", android.content.Context.MODE_PRIVATE)
    }
    var selectedCountry by remember {
        mutableStateOf(routePreferences.getString("preferred_country:" + appState.selectedGroupId, null))
    }
    // Upstream SelectServer always calls restartService, even if disconnected.
    // Keep the selected GUID persisted without starting the VPN prematurely.
    val selectActualServer: (String) -> Unit = { guid ->
        val state = mainViewModel.uiState.value
        if (NoraSelectionPolicy.shouldChange(state.selectedGuid, guid)) {
            if (NoraSelectionPolicy.mustRestart(state.isRunning)) {
                onAction(MainAction.SelectServer(guid))
            } else {
                mainViewModel.updateSelectedGuid(guid)
            }
        }
    }
    LaunchedEffect(appState.selectedGroupId) {
        selectedCountry = routePreferences.getString(
            "preferred_country:" + appState.selectedGroupId, null
        )
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    // Prevent double-taps from dispatching conflicting native start/stop commands.
    var powerPending by remember { mutableStateOf(false) }
    var expectedPowerState by remember { mutableStateOf<Boolean?>(null) }
    var powerTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(appState.isRunning, expectedPowerState) {
        if (expectedPowerState != null && appState.isRunning == expectedPowerState) {
            expectedPowerState = null
            powerPending = false
            powerTimedOut = false
            tapsell.onVpnOperationCompleted()
        }
    }
    // Permission prompts and service start may take time on OEM devices.
    // After the watchdog expires, allow a retry but never infer a connected VPN.
    LaunchedEffect(expectedPowerState) {
        val requestedState = expectedPowerState
        if (requestedState != null) {
            delay(15_000)
            if (expectedPowerState == requestedState &&
                mainViewModel.uiState.value.isRunning != requestedState) {
                powerTimedOut = true
                expectedPowerState = null
                powerPending = false
            }
        }
    }

    LaunchedEffect(appState.groups, appState.isTesting, loading) { refreshTick++ }
    // Native state updates and completed measurements invalidate cached profiles.
    // Avoid a perpetual 15s full-MMKV poll, particularly while the VPN is idle.
    // Measure native Xray delay for the connected server, never a fake latency.
    LaunchedEffect(appVisible, appState.isRunning, appState.selectedGuid, livePingEnabled) {
        if (appVisible && appState.isRunning &&
            !appState.selectedGuid.isNullOrBlank() && livePingEnabled) {
            delay(2500)
            while (appVisible && mainViewModel.uiState.value.isRunning) {
                if (!mainViewModel.uiState.value.isTesting &&
                    !mainViewModel.isLoading.value &&
                    !NoraForegroundSubscriptions.running.value) {
                    onAction(MainAction.TestCurrentServer)
                    delay(2000) // Refresh after a completed native measurement.
                    refreshTick++
                    delay(28_000)
                } else {
                    delay(3000) // Don't compete with full-group tests or sync writes.
                }
            }
        }
    }
    val nodes = remember(refreshTick, appState.selectedGroupId) {
        NoraRouteSelector.load(appState.selectedGroupId)
    }
    val ranked = remember(nodes) { NoraRouteSelector.rank(nodes) }
    val locations = remember(ranked) { NoraRouteSelector.locations(ranked) }
    val favoritePrefs = remember(context) {
        context.getSharedPreferences("nora_location_favorites", android.content.Context.MODE_PRIVATE)
    }
    var favoriteCountries by remember {
        mutableStateOf(favoritePrefs.getStringSet("country_codes", emptySet())?.toSet() ?: emptySet())
    }
    var locationQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val toggleFavorite: (String) -> Unit = { code ->
        if (!code.startsWith("UNKNOWN:")) {
            val next = if (code in favoriteCountries) favoriteCountries - code
                else favoriteCountries + code
            favoriteCountries = next
            favoritePrefs.edit().putStringSet("country_codes", next.toSet()).apply()
        }
    }
    val visibleLocations = remember(locations, locationQuery, favoriteCountries, favoritesOnly) {
        NoraRouteSelector.searchLocations(locations, locationQuery, favoriteCountries, favoritesOnly)
    }
    val prioritizedLocations = remember(locations, favoriteCountries) {
        NoraRouteSelector.searchLocations(locations, "", favoriteCountries)
    }
    val identity = remember(nodes) { nodes.joinToString("|") { it.guid } }
    val subscriptions = remember(appState.groups) { mainViewModel.getSubscriptions() }
    var usageByGroup by remember { mutableStateOf<Map<String, NoraSubscriptionUsage>>(emptyMap()) }
    var usageRefresh by remember { mutableIntStateOf(0) }
    // Load fresh quota/expiry for ALL enabled remote groups, not only the selected one.
    LaunchedEffect(subscriptions, usageRefresh) {
        val targets = subscriptions.filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
        coroutineScope {
            val limiter = Semaphore(4)
            val updates = targets.map { group ->
                async(Dispatchers.IO) {
                    group.guid to limiter.withPermit {
                        NoraSubscriptionUsageReader.fetch(group.subscription.url)
                    }
                }
            }.awaitAll().mapNotNull { (id, info) -> info?.let { id to it } }.toMap()
            usageByGroup = usageByGroup.filterKeys { id ->
                targets.any { it.guid == id }
            } + updates
        }
    }
    // Re-sync every enabled remote subscription whenever the app enters foreground.
    // The refresh runs off the UI thread, with a short debounce for ad/activity returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mainViewModel) {
        appVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                appVisible = true
                importScope.launch {
                    NoraForegroundSubscriptions.refresh {
                        mainViewModel.setupGroupTab(forceRefresh = true).join()
                        usageRefresh++
                        refreshTick++
                    }
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                appVisible = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appVisible = false
        }
    }
    // Register network callbacks only while the user can see NoraProxy.
    // A transport change requests fresh measurements, never a blind reconnect.
    val networkObserver = remember(context.applicationContext) {
        NoraNetworkObserver(context.applicationContext) {
            smartEngine.networkChanged()
            networkGeneration++
        }
    }
    DisposableEffect(networkObserver, smartMode, appVisible) {
        if (smartMode && appVisible) networkObserver.start()
        onDispose { networkObserver.stop() }
    }
    LaunchedEffect(networkGeneration, smartMode, appVisible) {
        if (networkGeneration > lastHandledNetworkGeneration) {
            lastHandledNetworkGeneration = networkGeneration
            if (smartMode && appVisible) {
                delay(4000) // Let Android finish switching transports.
                if (appVisible && mainViewModel.uiState.value.isRunning &&
                    !mainViewModel.uiState.value.isTesting &&
                    !mainViewModel.isLoading.value &&
                    !NoraForegroundSubscriptions.running.value) {
                    onAction(MainAction.TestRealAllServers)
                }
            }
        }
    }
    // Low-frequency quality check, foreground-only to protect battery and data.
    LaunchedEffect(smartMode, appVisible, appState.isRunning, appState.selectedGroupId) {
        if (smartMode && appVisible && appState.isRunning) {
            while (true) {
                delay(360_000)
                if (mainViewModel.uiState.value.isRunning &&
                    !mainViewModel.uiState.value.isTesting &&
                    !mainViewModel.isLoading.value &&
                    !NoraForegroundSubscriptions.running.value) {
                    onAction(MainAction.TestRealAllServers)
                }
            }
        }
    }
    // Only completed native all-server batches count as independent health probes.
    // A current-server status label is not sufficient evidence to restart a VPN.
    LaunchedEffect(appState.isTesting, appState.selectedGroupId) {
        if (appState.isTesting) {
            fullProbeStartedGroup = appState.selectedGroupId
        } else if (fullProbeStartedGroup == appState.selectedGroupId &&
            fullProbeStartedGroup != null) {
            fullProbeStartedGroup = null
            delay(450)
            val testedNodes = withContext(Dispatchers.IO) {
                NoraRouteSelector.load(appState.selectedGroupId)
            }
            smartEngine.recordCompletedProbe(testedNodes)
            refreshTick++
            if (smartMode && automaticRecovery && appVisible &&
                mainViewModel.uiState.value.isRunning && !powerPending &&
                !adShowing && !NoraForegroundSubscriptions.running.value) {
                val replacement = smartEngine.failoverCandidate(
                    testedNodes,
                    mainViewModel.uiState.value.selectedGuid,
                    routePreferences.getString("preferred_country:" + appState.selectedGroupId, null),
                    SystemClock.elapsedRealtime()
                )
                if (replacement != null) {
                    smartEngine.recordAutomaticSwitch(SystemClock.elapsedRealtime())
                    onAction(MainAction.SelectServer(replacement.guid))
                    Toast.makeText(context, "مسیر ناموفق بود؛ سرور جایگزین انتخاب شد",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // The active Xray GUID must belong to the visible group, not the previous one.
    LaunchedEffect(appState.selectedGroupId, identity) {
        if (!appState.isRunning && !syncingGroups &&
            ranked.isNotEmpty() && ranked.none { it.guid == appState.selectedGuid }) {
            val country = routePreferences.getString(
                "preferred_country:" + appState.selectedGroupId, null
            )
            val route = smartEngine.bestRoute(ranked, country)
                ?: smartEngine.bestRoute(ranked, null) ?: ranked.first()
            selectActualServer(route.guid)
        }
    }
    LaunchedEffect(identity, loading) {
        if (!loading && nodes.isNotEmpty() && nodes.all { it.latencyMs == 0L } &&
            !appState.isTesting) {
            onAction(MainAction.TestRealAllServers)
        }
    }
    val preferredRoute = if (smartMode) {
        smartEngine.bestRoute(ranked, selectedCountry)
    } else if (selectedCountry == null) {
        ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull()
    } else locations.firstOrNull { it.countryCode == selectedCountry }
    val connectionPhase = NoraConnectionPhase.resolve(
        isRunning = appState.isRunning,
        expectedRunning = expectedPowerState,
        hasRoute = preferredRoute != null,
        timedOut = powerTimedOut
    )
    // When connected, show the backend's actually selected route (not a visual-only choice).
    val activeRoute = ranked.firstOrNull { it.guid == appState.selectedGuid }
    // A preferred route is not proof of the currently active native server.
    val displayedRoute = if (appState.isRunning) activeRoute else preferredRoute
    val countryTitle = displayedRoute?.let { it.flag + "  " + it.countryName }
        ?: if (appState.isRunning) "مسیر متصل نامشخص" else "هنوز اشتراکی ندارید"
    val chooseRoute: (NoraNode?, String?) -> Unit = { route, country ->
        if (route != null) {
            smartEngine.recordManualChoice(SystemClock.elapsedRealtime())
            selectedCountry = country
            routePreferences.edit().apply {
                val preferenceKey = "preferred_country:" + appState.selectedGroupId
                if (country == null) remove(preferenceKey)
                else putString(preferenceKey, country)
            }.apply()
            // This goes through MainActivity.setSelectServer -> ViewModel/MMKV.
            // The activity restarts the native service if already running.
            selectActualServer(route.guid)
            tab = NoraTab.Home
        }
    }
    val brand = remember(context) {
        context.getSharedPreferences("nora_brand", android.content.Context.MODE_PRIVATE)
            .getString("seller", "").orEmpty()
    }
    // Use the user's actual cyan circuit-N brand art, not the old orange placeholder vector.
    val originalLogo = painterResource(R.drawable.nora_brand)

    val startOrStop: () -> Unit = {
        if (!powerPending && !adShowing) {
            powerTimedOut = false
            if (appState.isRunning) {
                powerPending = true
                expectedPowerState = false
                onAction(MainAction.ToggleService)
            } else {
                preferredRoute?.let { route ->
                    powerPending = true
                    expectedPowerState = true
                    selectActualServer(route.guid)
                    // The upstream MainActivity handles VPN permission and starts Xray.
                    onAction(MainAction.ToggleService)
                }
            }
        }
        Unit
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = cyan, secondary = blue, background = ink, surface = surface,
        onSurface = light, onBackground = light
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = ink,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NoraBottomBar(
                        active = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.background(surface).navigationBarsPadding()
                    )
                }
            ) { inner ->
                if (tab == NoraTab.Home) {
                    NoraHomeContent(
                        modifier = Modifier.fillMaxSize().padding(inner),
                        logo = originalLogo,
                        seller = brand,
                        connected = appState.isRunning,
                        phase = connectionPhase,
                        lastOperationTimedOut = powerTimedOut,
                        waiting = powerPending || adShowing,
                        canConnect = preferredRoute != null,
                        selectedCountryName = displayedRoute?.countryName ?:
                            (if (appState.isRunning) "مسیر فعلی نامشخص" else "بدون لوکیشن"),
                        locationFlag = displayedRoute?.flag ?: "🌐",
                        locationPing = if (appState.isRunning && !livePingEnabled) null
                            else displayedRoute?.latencyMs,
                        usage = usageByGroup[appState.selectedGroupId],
                        quickLocations = prioritizedLocations,
                        selectedCountryCode = selectedCountry,
                        testing = appState.isTesting,
                        hasUpdate = updateState.available,
                        onToggle = startOrStop,
                        onChooseLocation = { tab = NoraTab.Locations },
                        onSelectCountry = { country ->
                            chooseRoute(if (smartMode) smartEngine.bestRoute(ranked, country.countryCode)
                                ?: country else country, country.countryCode)
                        },
                        onSelectSmart = {
                            chooseRoute(if (smartMode) smartEngine.bestRoute(ranked, null)
                                else ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull(), null)
                        },
                        onOpenUpdate = { tab = NoraTab.Settings }
                    )
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(ink, Color(0xFF152943), ink)))
                        .padding(inner)
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { NoraHeader(originalLogo, brand, appState.isRunning, connectionPhase) }
                    when (tab) {
                        NoraTab.Home -> Unit
                        NoraTab.Locations -> {
                            item {
                                NoraSection("لوکیشن‌ها", "")
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = locationQuery,
                                    onValueChange = { locationQuery = it.take(80) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("جست‌وجوی کشور، شهر یا کد کشور") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { favoritesOnly = !favoritesOnly },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(if (favoritesOnly) "★ علاقه‌مندی‌ها"
                                            else "☆ علاقه‌مندی‌ها", fontSize = 12.sp)
                                    }
                                    Text(persianNumber(visibleLocations.size.toString()) + " کشور",
                                        color = muted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                if (!favoritesOnly && locationQuery.isBlank()) {
                                    NoraLocationCard("✦", "انتخاب هوشمند", "انتخاب بهترین مسیر شناخته‌شده",
                                        null, selectedCountry == null) {
                                        chooseRoute(if (smartMode) smartEngine.bestRoute(ranked, null)
                                            else ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull(), null)
                                    }
                                }
                            }
                            if (visibleLocations.isEmpty()) {
                                item {
                                    Text(if (favoritesOnly && favoriteCountries.isEmpty())
                                            "برای افزودن کشور به علاقه‌مندی‌ها، روی ستاره کنار آن بزنید."
                                        else "کشوری مطابق جست‌وجو پیدا نشد.",
                                        color = muted, fontSize = 12.sp)
                                }
                            }
                            items(visibleLocations, key = { it.countryCode }) { country ->
                                NoraLocationCard(country.flag, country.countryName,
                                    "مناسب‌ترین سرور این کشور", country.latencyMs,
                                    selectedCountry == country.countryCode &&
                                        activeRoute?.countryCode == country.countryCode,
                                    onFavorite = if (country.countryCode.startsWith("UNKNOWN:")) null
                                        else { { toggleFavorite(country.countryCode) } },
                                    favorite = country.countryCode in favoriteCountries) {
                                    chooseRoute(if (smartMode)
                                        smartEngine.bestRoute(ranked, country.countryCode) ?: country
                                        else country, country.countryCode)
                                }
                            }
                            item {
                                NoraSection("کیفیت اتصال", "مدیریت سرورها و سنجش مسیرها")
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    NoraMetric("سرورهای قابل انتخاب", nodes.size.toString(),
                                        Modifier.weight(1f))
                                    NoraMetric("لوکیشن‌ها", locations.size.toString(),
                                        Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { onAction(MainAction.TestRealAllServers) },
                                    enabled = !appState.isTesting && nodes.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(17.dp)
                                ) {
                                    Text(if (appState.isTesting) "در حال بررسی مسیرها..."
                                    else "سنجش پینگ واقعی سرورها")
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("تأخیرها آخرین نتایج اندازه‌گیری هستند؛ برای نتیجه دقیق‌تر پس از تغییر شبکه دوباره تست بگیرید.",
                                    fontSize = 12.sp, lineHeight = 21.sp, color = muted)
                            }
                        }
                        NoraTab.Subscription -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { importExpanded = !importExpanded }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("افزودن کانفیگ یا اشتراک", color = light,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(if (importExpanded) "−" else "＋",
                                        color = cyan, fontSize = 22.sp)
                                }
                            }
                            if (importExpanded) {
                                item {
                                    Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        OutlinedTextField(
                                            value = subscription,
                                            onValueChange = { subscription = it.take(65536) },
                                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                                            label = { Text("لینک اشتراک یا کانفیگ خام") },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        Button(
                                            onClick = {
                                                val input = subscription.trim()
                                                if (input.isNotEmpty()) {
                                                    when (val parsed = NoraImportRouter.classify(input)) {
                                                        is NoraImportPayload.Invalid ->
                                                            Toast.makeText(context, parsed.reason, Toast.LENGTH_LONG).show()
                                                        else -> {
                                                            onAction(MainAction.ImportBatchConfig(input))
                                                            subscription = ""
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = subscription.isNotBlank() && !loading && !groupBusy,
                                            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                                        ) { Text("وارد کردن خودکار", fontWeight = FontWeight.Bold) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onAction(MainAction.ImportQRcode) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(15.dp)
                                            ) { Text("اسکن QR", fontSize = 12.sp) }
                                            OutlinedButton(
                                                onClick = { galleryPicker.launch("image/*") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(15.dp)
                                            ) { Text("QR از گالری", fontSize = 12.sp) }
                                        }
                                        if (loading) CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                                }
                            }
                            item {
                                NoraSection("گروه‌های من", "")
                                if (syncingGroups) {
                                    Text("در حال به‌روزرسانی خودکار گروه‌های فعال…",
                                        color = cyan, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            if (subscriptions.isEmpty()) {
                                item {
                                    Text("هنوز اشتراکی اضافه نشده است.",
                                        color = muted, fontSize = 13.sp, lineHeight = 23.sp)
                                }
                            }
                            items(subscriptions, key = { "group-" + it.guid }) { group ->
                                NoraSubscriptionGroupCard(
                                    name = when (group.subscription.remarks.trim().lowercase()) {
                                        "default" -> "کانفیگ‌های شخصی"
                                        "import sub" -> "اشتراک واردشده"
                                        else -> group.subscription.remarks.ifBlank { "اشتراک بدون نام" }
                                    },
                                    numberOfNodes = MmkvManager.decodeServerList(group.guid).size,
                                    active = group.guid == appState.selectedGroupId,
                                    usage = usageByGroup[group.guid],
                                    onEdit = { editingGroup = group; editorVisible = true },
                                    onDelete = {
                                        if (appState.isRunning) {
                                            Toast.makeText(context, "ابتدا VPN را قطع کنید", Toast.LENGTH_LONG).show()
                                        } else deletingGroup = group
                                    },
                                    onSelect = {
                                        val savedCountry = routePreferences.getString(
                                            "preferred_country:" + group.guid, null
                                        )
                                        val routes = NoraRouteSelector.rank(NoraRouteSelector.load(group.guid))
                                        if (routes.isEmpty() && appState.isRunning) {
                                            Toast.makeText(context,
                                                "برای انتخاب گروه بدون سرور ابتدا اتصال را قطع کنید",
                                                Toast.LENGTH_LONG).show()
                                        } else {
                                            smartEngine.recordManualChoice(SystemClock.elapsedRealtime())
                                            selectedCountry = savedCountry
                                            onAction(MainAction.SelectGroup(group.guid))
                                            val selected = (if (smartMode)
                                                smartEngine.bestRoute(routes, savedCountry)
                                                else NoraRouteSelector.locations(routes).firstOrNull {
                                                    it.countryCode == savedCountry
                                                }) ?: routes.firstOrNull { it.latencyMs > 0 } ?: routes.firstOrNull()
                                            if (selected != null && selected.guid != appState.selectedGuid) {
                                                selectActualServer(selected.guid)
                                            }
                                        }
                                    }
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = {
                                        onAction(MainAction.UpdateSubscriptions)
                                        usageRefresh++
                                    },
                                    enabled = !loading && !syncingGroups && appState.selectedGroupId.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp)
                                ) { Text("به‌روزرسانی گروه انتخاب‌شده") }
                            }
                        }
                        NoraTab.Settings -> {
                            item { NoraSection("تنظیمات", "") }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(17.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("اتصال هوشمند", color = light,
                                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("اولویت پینگ پایدار و بررسی تغییر شبکه",
                                                    color = muted, fontSize = 11.sp)
                                            }
                                            Switch(checked = smartMode, onCheckedChange = {
                                                smartMode = it
                                                smartPreferences.edit().putBoolean("smart_mode", it).apply()
                                            })
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("بازیابی خودکار اتصال", color = light,
                                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text("فقط پس از دو تست ناموفق و با فاصله ایمن",
                                                    color = muted, fontSize = 11.sp)
                                            }
                                            Switch(
                                                checked = automaticRecovery,
                                                enabled = smartMode,
                                                onCheckedChange = {
                                                    automaticRecovery = it
                                                    smartPreferences.edit()
                                                        .putBoolean("auto_recovery", it).apply()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("پینگ اتصال", color = light, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text("اندازه‌گیری خودکار هر ۳۰ ثانیه", color = muted, fontSize = 12.sp)
                                        }
                                        Switch(checked = livePingEnabled, onCheckedChange = {
                                            livePingEnabled = it
                                            pingPrefs.edit().putBoolean("live_ping", it).apply()
                                        })
                                    }
                                }
                            }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("به‌روزرسانی برنامه", color = light, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("نسخه " + com.v2ray.ang.BuildConfig.VERSION_NAME, color = muted, fontSize = 12.sp)
                                        Text(updateState.message, color = cyan, fontSize = 12.sp, maxLines = 2)
                                        if (updateState.busy) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                            Text("دانلود: " + updateState.progress + "٪", color = light, fontSize = 12.sp)
                                        }
                                        Button(onClick = {
                                            if (updateState.downloaded) updater.install()
                                            else if (updateState.available) updater.download()
                                            else updater.check()
                                        }, enabled = !updateState.busy, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp)) {
                                            Text(if (updateState.downloaded) "نصب نسخه جدید"
                                                else if (updateState.available) "دانلود نسخه جدید"
                                                else "بررسی به‌روزرسانی", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            if (editorVisible) {
                NoraGroupEditorDialog(
                    existing = editingGroup,
                    busy = groupBusy,
                    onDismiss = { editorVisible = false },
                    onSave = { name ->
                        groupBusy = true
                        importScope.launch {
                            try {
                                val groupId = withContext(Dispatchers.IO) {
                                    NoraGroupRepository.save(editingGroup, name)
                                }
                                editorVisible = false
                                mainViewModel.setupGroupTab(forceRefresh = true).join()
                                if (!appState.isRunning) {
                                    onAction(MainAction.SelectGroup(groupId))
                                }
                                refreshTick++
                                Toast.makeText(context,
                                    if (appState.isRunning) "گروه ذخیره شد؛ برای انتخاب آن ابتدا VPN را قطع کنید"
                                    else "گروه ذخیره و انتخاب شد",
                                    Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "ذخیره گروه ناموفق بود", Toast.LENGTH_LONG).show()
                            } finally {
                                groupBusy = false
                            }
                        }
                    }
                )
            }
            deletingGroup?.let { target ->
                NoraDeleteGroupDialog(
                    name = target.subscription.remarks,
                    busy = groupBusy,
                    onDismiss = { deletingGroup = null },
                    onDelete = {
                        if (appState.isRunning) {
                            Toast.makeText(context, "ابتدا اتصال را قطع کنید", Toast.LENGTH_LONG).show()
                        } else {
                            groupBusy = true
                            importScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        NoraGroupRepository.remove(target.guid)
                                    }
                                    deletingGroup = null
                                    onAction(MainAction.RefreshGroups)
                                    refreshTick++
                                    Toast.makeText(context, "گروه حذف شد", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "حذف گروه ناموفق بود", Toast.LENGTH_LONG).show()
                                } finally {
                                    groupBusy = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}


/**
 * Home is deliberately a fixed-height, non-scrollable screen.
 * The connection control scales to the available device viewport, while all
 * secondary metrics and network diagnostics live under Locations.
 */
@Composable
private fun NoraHomeContent(
    modifier: Modifier = Modifier,
    logo: Painter,
    seller: String,
    connected: Boolean,
    phase: NoraConnectionPhase,
    lastOperationTimedOut: Boolean,
    waiting: Boolean,
    canConnect: Boolean,
    selectedCountryName: String,
    locationFlag: String,
    locationPing: Long?,
    usage: NoraSubscriptionUsage?,
    quickLocations: List<NoraNode>,
    selectedCountryCode: String?,
    testing: Boolean,
    hasUpdate: Boolean,
    onToggle: () -> Unit,
    onChooseLocation: () -> Unit,
    onSelectCountry: (NoraNode) -> Unit,
    onSelectSmart: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    // The timer is explicitly a foreground observation timer, not fabricated
    // service uptime. Xray's running state is still the single source of truth.
    var observedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(connected) {
        observedSeconds = 0
        if (connected) {
            val started = SystemClock.elapsedRealtime()
            while (true) {
                observedSeconds = ((SystemClock.elapsedRealtime() - started) / 1000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                delay(1000)
            }
        }
    }
    val observedTime = String.format(
        java.util.Locale.US, "%02d:%02d:%02d",
        observedSeconds / 3600, observedSeconds / 60 % 60, observedSeconds % 60
    )
    val fastest = quickLocations.firstOrNull { it.latencyMs > 0L }
        ?: quickLocations.firstOrNull()
    BoxWithConstraints(
        modifier = modifier.background(ink).padding(horizontal = 15.dp)
    ) {
        // Preserve a completely visible power button on small Android screens.
        val compact = maxHeight < 665.dp
        val tiny = maxHeight < 525.dp
        val heroHeight = (maxHeight * (if (tiny) .32f else if (compact) .34f else .37f))
            .coerceIn(152.dp, 286.dp)
        val buttonSize = (heroHeight * .53f).coerceIn(100.dp, 148.dp)
        val sectionGap = if (tiny) 6.dp else if (compact) 8.dp else 11.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                // These nested rings are drawn locally and require neither an
                // external map service nor high-cost infinite animations.
                Canvas(Modifier.fillMaxSize()) {
                    val anchor = androidx.compose.ui.geometry.Offset(
                        center.x, size.height * 1.03f
                    )
                    val r = size.width * .95f
                    drawCircle(Color(0xFF04323A), r, anchor)
                    drawCircle(Color(0xFF072732), r * .77f, anchor)
                    drawCircle(Color(0xFF07504F), r * .61f, anchor)
                    drawCircle(Color(0xFF092934), r * .45f, anchor)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x5525B6A5), Color.Transparent),
                            center = anchor,
                            radius = r * .75f
                        ),
                        radius = r * .75f,
                        center = anchor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = logo, contentDescription = "NoraProxy",
                        modifier = Modifier.size(if (tiny) 35.dp else 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Text("NoraProxy", color = light, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            if (seller.isBlank()) "Secure connection" else seller,
                            color = muted, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        when (phase) {
                            NoraConnectionPhase.CONNECTED -> "● متصل"
                            NoraConnectionPhase.CONNECTING -> "● در حال اتصال"
                            NoraConnectionPhase.DISCONNECTING -> "● در حال قطع"
                            NoraConnectionPhase.FAILED -> "● خطای اتصال"
                            NoraConnectionPhase.NO_ROUTE -> "● بدون اشتراک"
                            NoraConnectionPhase.READY -> "● آماده"
                        },
                        color = if (connected) green else if (phase.isBusy) amber else muted,
                        fontSize = 11.sp, maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    NoraPowerButton(
                        connected = connected,
                        phase = phase,
                        enabled = !waiting && (connected || canConnect),
                        diameter = buttonSize,
                        onClick = onToggle
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        waiting && !phase.isBusy -> "نمایش تبلیغ..."
                        connected -> "Connected  |  " + observedTime
                        else -> phase.statusLabel
                    },
                    color = light,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (tiny) 14.sp else 17.sp,
                    maxLines = 1
                )
                if (!tiny) {
                    Text(
                        when {
                            connected -> "زمان نمایش اتصال در برنامه"
                            lastOperationTimedOut -> "تغییر وضعیت تأیید نشد؛ دوباره بررسی کنید"
                            else -> phase.actionHint
                        },
                        color = muted, fontSize = 10.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasUpdate) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF234E4B))
                        .clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نسخه جدید NoraProxy آماده است", color = green,
                        fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("←", color = light)
                }
            }
            // A real backend-selected location, never an invented IP or server.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(surface)
                    .clickable(onClick = onChooseLocation)
                    .padding(horizontal = 14.dp, vertical = if (tiny) 9.dp else 11.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(locationFlag, fontSize = 27.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Selected Location", color = muted, fontSize = 11.sp)
                    Text(selectedCountryName, color = light,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("←", color = light, fontSize = 22.sp)
                    if (locationPing != null && locationPing > 0L) {
                        Text(persianNumber(locationPing.toString()) + " ms",
                            color = green, fontSize = 11.sp)
                    } else if (testing) {
                        Text("سنجش...", color = amber, fontSize = 10.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                NoraRouteShortcut(
                    modifier = Modifier.weight(1f),
                    icon = "ϟ",
                    title = "Smart Location",
                    subtitle = if (selectedCountryCode == null) "فعال" else "بهترین مسیر",
                    selected = selectedCountryCode == null,
                    enabled = quickLocations.isNotEmpty(),
                    onClick = onSelectSmart
                )
                NoraRouteShortcut(
                    modifier = Modifier.weight(1f),
                    icon = "◷",
                    title = "Fastest Location",
                    subtitle = fastest?.countryName ?: "بدون لوکیشن",
                    selected = fastest != null && selectedCountryCode == fastest.countryCode,
                    enabled = fastest != null,
                    onClick = { fastest?.let(onSelectCountry) }
                )
            }
            if (!tiny) {
                // Decorative network globe. Does not imply coordinates, ISP or
                // an external IP: those values are not present in the native state.
                Box(
                    Modifier.fillMaxWidth()
                        .height(if (compact) 91.dp else 112.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF112C38))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val lineColor = Color(0xFF527A80).copy(alpha = .33f)
                        val middle = androidx.compose.ui.geometry.Offset(
                            size.width * .34f, size.height * .58f
                        )
                        val radius = size.height * .59f
                        drawCircle(lineColor, radius, middle, style = Stroke(width = 1.dp.toPx()))
                        drawOval(
                            color = lineColor,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                middle.x - radius * .5f, middle.y - radius
                            ),
                            size = androidx.compose.ui.geometry.Size(radius, radius * 2f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        for (fraction in listOf(-.48f, 0f, .48f)) {
                            val y = middle.y + radius * fraction
                            drawLine(
                                lineColor,
                                androidx.compose.ui.geometry.Offset(middle.x - radius * .82f, y),
                                androidx.compose.ui.geometry.Offset(middle.x + radius * .82f, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        drawCircle(
                            Color(0xFF12B9A8).copy(alpha = .19f),
                            radius * .24f, middle
                        )
                        drawCircle(Color(0xFF16CDB6), 5.dp.toPx(), middle)
                    }
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xEE173744))
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Secure Network", color = muted, fontSize = 10.sp)
                        Text(locationFlag + "  " + selectedCountryName,
                            color = light, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            if (connected) "● VPN active"
                            else if (canConnect) "○ Ready to connect" else "○ No route",
                            color = if (connected) green else muted, fontSize = 10.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                NoraInfoTile(
                    title = "Remaining Traffic",
                    value = usage?.remainingTrafficLabel() ?: "نامشخص",
                    modifier = Modifier.weight(1f)
                )
                NoraInfoTile(
                    title = "Subscription",
                    value = usage?.remainingTimeLabel() ?: "نامشخص",
                    modifier = Modifier.weight(1f)
                )
            }
            if (!compact) {
                Text(
                    "اطلاعات اتصال و مصرف فقط از سرویس و اشتراک واقعی خوانده می‌شود",
                    color = muted, fontSize = 9.sp, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NoraRouteShortcut(
    modifier: Modifier,
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color(0xFF1A4549) else surface)
            .border(1.dp, if (selected) cyan.copy(alpha = .35f)
                else edge.copy(alpha = .15f), RoundedCornerShape(17.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = light, fontSize = 11.sp,
                modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(icon, color = green, fontSize = 16.sp)
        }
        Text(subtitle, color = if (selected) green else muted, fontSize = 11.sp,
            fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoraInfoTile(
    title: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(surface)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, color = muted, fontSize = 11.sp, maxLines = 1)
        Text(value, color = light, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoraDashboardMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = muted, fontSize = 11.sp, maxLines = 1)
        Text(value, color = light, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoraQuickCountryCard(
    flag: String,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = Modifier
            .width(if (compact) 100.dp else 112.dp)
            .heightIn(min = if (compact) 56.dp else 72.dp)
            .clip(shape)
            .background(if (selected)
                Brush.verticalGradient(listOf(Color(0xFF294B76), Color(0xFF21354E)))
                else Brush.verticalGradient(listOf(Color(0xFF16283D), surface)))
            .border(1.dp, if (selected) cyan.copy(alpha = .78f)
                else edge.copy(alpha = .34f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = if (compact) 7.dp else 10.dp, horizontal = 7.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(flag, fontSize = if (compact) 21.sp else 25.sp, maxLines = 1)
        Text(label, color = if (selected) cyan else light,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoraHeader(
    painter: Painter, seller: String, connected: Boolean, phase: NoraConnectionPhase
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painter = painter, contentDescription = "NoraProxy logo",
            modifier = Modifier.size(59.dp).clip(RoundedCornerShape(18.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text("NoraProxy", color = light, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = .3.sp)
            Text(if (seller.isBlank()) "امنیت ساده، کنترل هوشمند" else "ارائه‌شده توسط " + seller,
                color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(if (connected) Color(0xFF1A4946) else surface2)
            .border(1.dp, if (connected) green.copy(alpha = .4f) else edge.copy(alpha = .55f),
                RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            // The full native phase remains visible in the dashboard below.
            // Keep the toolbar badge short on 360dp devices and at larger font scales.
            val compactStatus = when (phase) {
                NoraConnectionPhase.NO_ROUTE -> "بدون اشتراک"
                NoraConnectionPhase.READY -> "آماده"
                NoraConnectionPhase.CONNECTING -> "اتصال..."
                NoraConnectionPhase.CONNECTED -> "متصل"
                NoraConnectionPhase.DISCONNECTING -> "قطع..."
                NoraConnectionPhase.FAILED -> "تأیید نشد"
            }
            Text(compactStatus,
                color = if (connected) green else if (phase.isBusy || phase == NoraConnectionPhase.FAILED) amber else muted,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NoraPowerButton(
    connected: Boolean,
    phase: NoraConnectionPhase,
    enabled: Boolean,
    diameter: Dp = 132.dp,
    onClick: () -> Unit
) {
    val accent by animateColorAsState(if (connected) green else cyan, label = "powerAccent")
    Box(
        modifier = Modifier.size(diameter)
            .clip(CircleShape)
            .background(Color(0xFF0E2A36))
            .border(1.dp, accent.copy(alpha = .13f), CircleShape)
            .semantics {
                contentDescription = if (!enabled && phase.isBusy) phase.statusLabel
                    else if (!enabled) "اتصال در دسترس نیست"
                    else if (connected) "قطع اتصال NoraProxy"
                    else "اتصال NoraProxy"
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter * .43f)) {
            val width = (diameter.value * .065f).dp.toPx()
            drawArc(
                color = if (enabled) accent else muted,
                startAngle = -45f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
            drawLine(
                color = if (enabled) accent else muted,
                start = androidx.compose.ui.geometry.Offset(center.x, size.height * .02f),
                end = androidx.compose.ui.geometry.Offset(center.x, size.height * .48f),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun NoraSubscriptionGroupCard(
    name: String,
    numberOfNodes: Int,
    active: Boolean,
    usage: NoraSubscriptionUsage?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    var actionsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (active)
                Brush.horizontalGradient(listOf(Color(0xFF243E5D), surface2))
                else Brush.horizontalGradient(listOf(surface, surface)))
            .border(1.dp, if (active) cyan.copy(alpha = .55f)
                else edge.copy(alpha = .28f), shape)
            .clickable(onClick = onSelect)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = light, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (active) "● فعال" else "انتخاب  ←", color = if (active) cyan else muted,
                fontSize = 12.sp)
        }
        Text(persianNumber(numberOfNodes.toString()) + " سرور", color = muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(usage?.remainingTrafficLabel() ?: "حجم نامشخص",
                modifier = Modifier.weight(1f), color = light, fontSize = 12.sp)
            Text(usage?.remainingTimeLabel() ?: "زمان نامشخص",
                modifier = Modifier.weight(1f), color = muted, fontSize = 12.sp,
                textAlign = TextAlign.End)
        }
        usage?.usagePercent()?.let { percent ->
            Box(Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color(0xFF28435E))) {
                Box(Modifier.fillMaxWidth(percent).height(5.dp).background(cyan))
            }
            if (usage.used != null && usage.total != null) {
                Text(
                    "مصرف‌شده: " + formatBytes(usage.used!!) +
                        " از " + formatBytes(usage.total!!),
                    color = muted, fontSize = 11.sp
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { actionsExpanded = !actionsExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مدیریت گروه", color = muted, fontSize = 11.sp)
            Text(if (actionsExpanded) "⌃" else "⌄", color = cyan, fontSize = 17.sp)
        }
        if (actionsExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)) { Text("ویرایش", fontSize = 12.sp) }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)) { Text("حذف", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun NoraSection(title: String, caption: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.width(3.dp).height(if (caption.isBlank()) 27.dp else 39.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(cyan, blue)))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = light, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (caption.isNotBlank()) Text(caption, color = muted, fontSize = 12.sp)
        }
        Text("NORA", color = cyan.copy(alpha = .64f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
private fun NoraMetric(label: String, number: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number, fontSize = 24.sp, color = cyan, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = muted)
        }
    }
}

@Composable
private fun NoraLocationCard(
    emoji: String, title: String, subtitle: String,
    delay: Long?, selected: Boolean, compact: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    favorite: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(21.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (selected) listOf(Color(0xFF264A70), Color(0xFF172B46))
                    else listOf(surface, Color(0xFF16283E))
                )
            )
            .border(1.dp, if (selected) cyan.copy(alpha = .7f)
                else edge.copy(alpha = .4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = if (compact) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(if (compact) 42.dp else 50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF243F5F))
                .border(1.dp, edge.copy(alpha = .55f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = if (compact) 25.sp else 28.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 15.sp, color = light, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (selected) "●  " + subtitle else subtitle, fontSize = 11.sp,
                color = if (selected) green else muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFavorite != null) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFavorite)
                    .semantics {
                        contentDescription = if (favorite)
                            "حذف از علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (favorite) "★" else "☆",
                    color = if (favorite) amber else muted, fontSize = 23.sp)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (delay != null && delay > 0L) Color(0xFF1C464A) else surface2)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (delay != null && delay > 0L) persianNumber(delay.toString()) + " ms"
                else "—", fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (delay != null && delay > 0L) green else muted)
        }
    }
}

@Composable
private fun NoraBottomBar(
    active: NoraTab,
    onSelect: (NoraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(Color(0xFF15303C))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoraTab.entries.forEach { tab ->
            val isActive = active == tab
            Column(
                modifier = Modifier.weight(1f)
                    .heightIn(min = 59.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isActive, role = Role.Tab,
                        onClick = { onSelect(tab) })
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = if (isActive) green else muted
                )
                Text(
                    if (tab == NoraTab.Home) "VPN" else tab.title,
                    color = if (isActive) light else muted,
                    fontSize = 11.sp, maxLines = 1,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
