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
private val ink = Color(0xFF090E1B)
private val surface = Color(0xFF121F32)
private val surface2 = Color(0xFF20364E)
private val cyan = Color(0xFF78B8FF)
private val blue = Color(0xFFFFAF85)
private val light = Color(0xFFF4F8FF)
private val muted = Color(0xFFAABBD1)
private val green = Color(0xFF6CE5C2)
private val amber = Color(0xFFFFCE88)
private val edge = Color(0xFF375572)
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
    val displayedRoute = if (appState.isRunning) activeRoute ?: preferredRoute else preferredRoute
    val countryTitle = displayedRoute?.let { it.flag + "  " + it.countryName }
        ?: "هنوز اشتراکی ندارید"
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
                        selectedCountryName = displayedRoute?.countryName ?: "بدون لوکیشن",
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
                    item { NoraHeader(originalLogo, brand, appState.isRunning) }
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
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(
                Color(0xFF090F1E), Color(0xFF13233B), Color(0xFF0B162A)
            ))
        ).padding(horizontal = 16.dp)
    ) {
        // A quiet, two-color ambient glow gives the home its own brand identity.
        // It is painted behind content, with no infinite animation or layout cost.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(cyan.copy(alpha = .14f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * .87f, size.height * .18f),
                    radius = size.width * .72f
                ),
                radius = size.width * .72f,
                center = androidx.compose.ui.geometry.Offset(size.width * .87f, size.height * .18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(blue.copy(alpha = .08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * .10f, size.height * .81f),
                    radius = size.width * .65f
                ),
                radius = size.width * .65f,
                center = androidx.compose.ui.geometry.Offset(size.width * .10f, size.height * .81f)
            )
        }
        val compact = maxHeight < 665.dp
        val tiny = maxHeight < 525.dp
        val powerDiameter = (maxHeight * (if (compact) 0.24f else 0.28f))
            .coerceIn(if (tiny) 110.dp else 132.dp, if (compact) 166.dp else 220.dp)
        val inset = if (compact) 6.dp else 10.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(top = inset, bottom = inset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NoraHeader(logo, seller, connected, phase)
            Spacer(Modifier.weight(0.45f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(if (connected) Color(0xFF173F3D) else Color(0xFF20344C))
                    .border(1.dp, if (connected) green.copy(alpha = .32f)
                        else edge.copy(alpha = .8f), RoundedCornerShape(40.dp))
                    .padding(horizontal = 15.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp)
                    .background(if (waiting) amber else if (connected) green else muted,
                        CircleShape))
                Text(
                    if (waiting && !phase.isBusy) "نمایش تبلیغ"
                    else phase.statusLabel,
                    color = if (waiting) amber else if (connected) green else light,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Text(
                if (waiting && !phase.isBusy) "در حال نمایش تبلیغ"
                else phase.headline,
                color = light, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 17.sp else 20.sp,
                textAlign = TextAlign.Center, maxLines = 1
            )
            if (!tiny) Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            NoraPowerButton(
                connected = connected,
                phase = phase,
                enabled = !waiting && (connected || canConnect),
                diameter = powerDiameter,
                onClick = onToggle
            )
            if (!tiny) Text(
                if (lastOperationTimedOut && connected)
                    "قطع اتصال تأیید نشد؛ سرویس هنوز روشن است"
                else if (waiting && !phase.isBusy) "لطفاً تا پایان نمایش صبر کنید"
                else phase.actionHint,
                color = muted, fontSize = 11.sp, maxLines = 1
            )
            Spacer(Modifier.weight(0.7f))
            if (hasUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(
                            Color(0xFF28385C), Color(0xFF18344E))))
                        .border(1.dp, blue.copy(alpha = .33f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نسخه جدید آماده است", modifier = Modifier.weight(1f),
                        color = Color(0xFFFFD3B8), fontSize = 11.sp)
                    Text("←", color = light, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            // A single route card presents the backend-selected GUID, not a visual-only choice.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("مسیر اتصال", modifier = Modifier.weight(1f),
                    color = light, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("همه لوکیشن‌ها  ←", modifier = Modifier.clickable(onClick = onChooseLocation),
                    color = cyan, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(
                        Color(0xFF253D5C), Color(0xFF172840))))
                    .border(1.dp, edge.copy(alpha = .75f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onChooseLocation)
                    .padding(horizontal = 13.dp, vertical = if (compact) 9.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp)
                    .background(Color(0xFF29466A), RoundedCornerShape(14.dp))
                    .border(1.dp, cyan.copy(alpha = .18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(locationFlag, fontSize = 25.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(selectedCountryName, color = light, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (connected) "مسیر متصل" else "مسیر انتخاب‌شده", color = muted,
                        fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (locationPing != null && locationPing > 0L)
                                Color(0xFF1F524C) else surface2)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (locationPing != null && locationPing > 0L)
                                persianNumber(locationPing.toString()) + " ms"
                            else if (testing) "در حال سنجش" else "—",
                            color = if (locationPing != null && locationPing > 0L) green else muted,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    }
                    if (!tiny) Text("پینگ واقعی", color = muted,
                        fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(if (compact) 7.dp else 11.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF15253D))
                    .border(1.dp, edge.copy(alpha = .35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 13.dp, vertical = if (compact) 10.dp else 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NoraDashboardMetric(
                    "حجم باقی‌مانده",
                    usage?.remainingTrafficLabel() ?: "نامشخص",
                    Modifier.weight(1f)
                )
                Box(Modifier.width(1.dp).height(35.dp).background(surface2))
                NoraDashboardMetric(
                    "اعتبار اشتراک",
                    usage?.remainingTimeLabel() ?: "نامشخص",
                    Modifier.weight(1f)
                )
            }
            if (!tiny) usage?.usagePercent()?.let { used ->
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(6.dp)).background(surface2)) {
                    Box(Modifier.fillMaxWidth(used).height(3.dp).background(cyan))
                }
            }
            if (!tiny) {
            Spacer(Modifier.height(if (compact) 8.dp else 13.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("انتخاب سریع کشور", modifier = Modifier.weight(1f),
                    color = light, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(persianNumber(quickLocations.size.toString()) + " لوکیشن",
                    color = muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(7.dp))
            if (quickLocations.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .background(surface).clickable(onClick = onChooseLocation)
                        .heightIn(min = 48.dp).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌍", fontSize = 18.sp)
                    Text("برای نمایش کشورها، اشتراک اضافه کنید", color = muted, fontSize = 12.sp)
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp)
                ) {
                    item(key = "smart") {
                        NoraQuickCountryCard("✦", "هوشمند",
                            selected = selectedCountryCode == null,
                            compact = compact, onClick = onSelectSmart)
                    }
                    items(quickLocations, key = { it.countryCode }) { route ->
                        NoraQuickCountryCard(route.flag, route.countryName,
                            selected = selectedCountryCode == route.countryCode,
                            compact = compact, onClick = { onSelectCountry(route) })
                    }
                }
            }
            } // Compact devices retain route and quota while omitting the secondary carousel.
        }
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
            Text(if (seller.isBlank()) "اتصال سریع و ایمن" else "ارائه‌شده توسط " + seller,
                color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(if (connected) Color(0xFF1A4946) else surface2)
            .border(1.dp, if (connected) green.copy(alpha = .4f) else edge.copy(alpha = .55f),
                RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(phase.statusLabel,
                color = if (connected) green else if (phase.isBusy) amber else muted,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NoraPowerButton(
    connected: Boolean,
    phase: NoraConnectionPhase,
    enabled: Boolean,
    diameter: Dp = 272.dp,
    onClick: () -> Unit
) {
    val ring by animateColorAsState(if (connected) green else cyan, label = "ring")
    val glow by animateFloatAsState(if (connected) 1f else .65f, label = "glow")
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                brush = Brush.radialGradient(listOf(
                    ring.copy(alpha = .17f * glow),
                    blue.copy(alpha = .07f * glow), Color.Transparent
                )),
                radius = radius
            )
            drawCircle(ring.copy(alpha = .14f), radius = radius * .89f,
                style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(
                brush = Brush.sweepGradient(listOf(cyan, blue, cyan, green, cyan)),
                radius = radius * .76f,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(ring.copy(alpha = .13f), radius = radius * .62f,
                style = Stroke(width = 1.dp.toPx()))
        }
        Box(modifier = Modifier.size(diameter * .67f)
            .clip(CircleShape) // Prevent square ripple artifacts on tap.
            .background(
                Brush.radialGradient(listOf(Color(0xFF203F64), Color(0xFF12243D))),
                CircleShape
            )
            .border(1.dp, Color(0xFF5799BD), CircleShape)
            .semantics {
                contentDescription = if (!enabled && phase.isBusy) phase.statusLabel
                    else if (!enabled) "اتصال در دسترس نیست"
                    else if (connected) "قطع اتصال NoraProxy"
                    else "اتصال NoraProxy"
            }
            .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(diameter * .27f)) {
                val stroke = 8.dp.toPx()
                drawArc(ring, startAngle = -42f, sweepAngle = 264f,
                    useCenter = false, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawLine(ring,
                    start = androidx.compose.ui.geometry.Offset(center.x, center.y - size.height * .37f),
                    end = androidx.compose.ui.geometry.Offset(center.x, center.y + size.height * .04f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
            }
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
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.verticalGradient(listOf(
                Color(0xFF192D48), Color(0xFF102036))))
            .border(1.dp, edge.copy(alpha = .42f), RoundedCornerShape(23.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoraTab.entries.forEach { tab ->
            val isActive = active == tab
            Column(
                modifier = Modifier.weight(1f)
                    .heightIn(min = 59.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (isActive)
                        Brush.verticalGradient(listOf(
                            Color(0xFF315A85), Color(0xFF233954)))
                        else Brush.verticalGradient(listOf(
                            Color.Transparent, Color.Transparent)))
                    .selectable(selected = isActive, role = Role.Tab,
                        onClick = { onSelect(tab) })
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = null, // Tab label is announced once.
                    modifier = Modifier.size(23.dp),
                    tint = if (isActive) cyan else muted
                )
                Text(tab.title, color = if (isActive) light else muted,
                    fontSize = 11.sp, maxLines = 1,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
                if (isActive) Box(Modifier.width(20.dp).height(2.dp)
                    .clip(CircleShape).background(blue))
            }
        }
    }
}
