package com.v2ray.ang.ui.main

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
private val ink = Color(0xFF0C121B)
private val surface = Color(0xFF182431)
private val surface2 = Color(0xFF223341)
private val cyan = Color(0xFF76EAD0)
private val blue = Color(0xFF9388F6)
private val light = Color(0xFFF5F8FC)
private val muted = Color(0xFFA4B1C0)
private val green = Color(0xFF76EAD0)
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
    LaunchedEffect(appState.selectedGroupId) {
        selectedCountry = routePreferences.getString(
            "preferred_country:" + appState.selectedGroupId, null
        )
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    // Prevent double-taps from dispatching conflicting native start/stop commands.
    var powerPending by remember { mutableStateOf(false) }
    var expectedPowerState by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(appState.isRunning, expectedPowerState) {
        if (expectedPowerState != null && appState.isRunning == expectedPowerState) {
            expectedPowerState = null
            powerPending = false
            tapsell.onVpnOperationCompleted()
        }
    }
    // Re-enable the control if Android VPN permission was declined or startup failed.
    LaunchedEffect(expectedPowerState) {
        if (expectedPowerState != null) {
            delay(8000)
            if (expectedPowerState != null) {
                expectedPowerState = null
                powerPending = false
            }
        }
    }

    LaunchedEffect(appState.groups, appState.isTesting, loading) { refreshTick++ }
    // Native state updates and completed measurements invalidate cached profiles.
    // Avoid a perpetual 15s full-MMKV poll, particularly while the VPN is idle.
    // Measure native Xray delay for the connected server, never a fake latency.
    LaunchedEffect(appState.isRunning, appState.selectedGuid, livePingEnabled) {
        if (appState.isRunning && !appState.selectedGuid.isNullOrBlank() && livePingEnabled) {
            delay(2500)
            while (true) {
                onAction(MainAction.TestCurrentServer)
                delay(2000) // Fetch the stored result after the native measurement.
                refreshTick++
                delay(28_000)
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
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                importScope.launch {
                    NoraForegroundSubscriptions.refresh {
                        mainViewModel.setupGroupTab(forceRefresh = true).join()
                        usageRefresh++
                        refreshTick++
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The active Xray GUID must belong to the visible group, not the previous one.
    LaunchedEffect(appState.selectedGroupId, identity) {
        if (ranked.isNotEmpty() && ranked.none { it.guid == appState.selectedGuid }) {
            val country = routePreferences.getString(
                "preferred_country:" + appState.selectedGroupId, null
            )
            val route = NoraRouteSelector.locations(ranked).firstOrNull {
                it.countryCode == country
            } ?: ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.first()
            onAction(MainAction.SelectServer(route.guid))
        }
    }
    LaunchedEffect(identity, loading) {
        if (!loading && nodes.isNotEmpty() && nodes.all { it.latencyMs == 0L } &&
            !appState.isTesting) {
            onAction(MainAction.TestRealAllServers)
        }
    }
    val preferredRoute = if (selectedCountry == null) {
        ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull()
    } else locations.firstOrNull { it.countryCode == selectedCountry }
    // When connected, show the backend's actually selected route (not a visual-only choice).
    val activeRoute = ranked.firstOrNull { it.guid == appState.selectedGuid }
    val displayedRoute = if (appState.isRunning) activeRoute ?: preferredRoute else preferredRoute
    val countryTitle = displayedRoute?.let { it.flag + "  " + it.countryName }
        ?: "هنوز اشتراکی ندارید"
    val chooseRoute: (NoraNode?, String?) -> Unit = { route, country ->
        if (route != null) {
            selectedCountry = country
            routePreferences.edit().apply {
                val preferenceKey = "preferred_country:" + appState.selectedGroupId
                if (country == null) remove(preferenceKey)
                else putString(preferenceKey, country)
            }.apply()
            // This goes through MainActivity.setSelectServer -> ViewModel/MMKV.
            // The activity restarts the native service if already running.
            if (appState.selectedGuid != route.guid) {
                onAction(MainAction.SelectServer(route.guid))
            }
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
            if (appState.isRunning) {
                powerPending = true
                expectedPowerState = false
                onAction(MainAction.ToggleService)
            } else {
                preferredRoute?.let { route ->
                    powerPending = true
                    expectedPowerState = true
                    if (appState.selectedGuid != route.guid) {
                        onAction(MainAction.SelectServer(route.guid))
                    }
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
                        onSelectCountry = { country -> chooseRoute(country, country.countryCode) },
                        onSelectSmart = { chooseRoute(ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull(), null) },
                        onOpenUpdate = { tab = NoraTab.Settings }
                    )
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(ink, Color(0xFF0B2037), ink)))
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
                                        chooseRoute(ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull(), null)
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
                                    chooseRoute(country, country.countryCode)
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
                                            selectedCountry = savedCountry
                                            onAction(MainAction.SelectGroup(group.guid))
                                            val selected = NoraRouteSelector.locations(routes).firstOrNull {
                                                it.countryCode == savedCountry
                                            } ?: routes.firstOrNull { it.latencyMs > 0 } ?: routes.firstOrNull()
                                            if (selected != null && selected.guid != appState.selectedGuid) {
                                                onAction(MainAction.SelectServer(selected.guid))
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
            Brush.verticalGradient(listOf(ink, Color(0xFF141E2A), ink))
        ).padding(horizontal = 16.dp)
    ) {
        val compact = maxHeight < 665.dp
        val tiny = maxHeight < 525.dp
        val powerDiameter = (maxHeight * (if (compact) 0.24f else 0.28f))
            .coerceIn(if (tiny) 110.dp else 132.dp, if (compact) 166.dp else 220.dp)
        val inset = if (compact) 6.dp else 10.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(top = inset, bottom = inset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NoraHeader(logo, seller, connected)
            Spacer(Modifier.weight(0.45f))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(40.dp))
                    .background(if (connected) Color(0xFF1B3D39) else surface2)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(if (connected) green else muted, CircleShape))
                Text(
                    if (waiting) "در حال تغییر اتصال"
                    else if (connected) "تونل VPN فعال است" else "آماده اتصال",
                    color = if (connected) green else light,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Text(
                if (waiting) "در حال پردازش..."
                else if (connected) "سرویس VPN در حال اجراست"
                else "با یک لمس متصل شوید",
                color = light, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 17.sp else 20.sp,
                textAlign = TextAlign.Center, maxLines = 1
            )
            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            NoraPowerButton(
                connected = connected,
                enabled = !waiting && (connected || canConnect),
                diameter = powerDiameter,
                onClick = onToggle
            )
            Text(
                if (waiting) "در حال تغییر وضعیت اتصال"
                else if (connected) "برای قطع اتصال لمس کنید"
                else if (canConnect) "برای اتصال لمس کنید" else "ابتدا یک اشتراک اضافه کنید",
                color = muted, fontSize = 11.sp, maxLines = 1
            )
            Spacer(Modifier.weight(0.7f))
            if (hasUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2C46))
                        .clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نسخه جدید آماده است", modifier = Modifier.weight(1f),
                        color = Color(0xFFD1C8FF), fontSize = 11.sp)
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
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(surface)
                    .clickable(onClick = onChooseLocation)
                    .padding(horizontal = 13.dp, vertical = if (compact) 9.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(39.dp).background(surface2, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(locationFlag, fontSize = 25.sp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(selectedCountryName, color = light, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (connected) "مسیر متصل" else "مسیر انتخاب‌شده", color = muted,
                        fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (locationPing != null && locationPing > 0L)
                            persianNumber(locationPing.toString()) + " ms"
                        else if (testing) "در حال سنجش" else "پینگ نامشخص",
                        color = if (locationPing != null && locationPing > 0L) green else muted,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                    )
                    if (!tiny) Text("پینگ واقعی", color = muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(if (compact) 7.dp else 11.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(surface).padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 13.dp),
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
            usage?.usagePercent()?.let { used ->
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(6.dp)).background(surface2)) {
                    Box(Modifier.fillMaxWidth(used).height(3.dp).background(cyan))
                }
            }
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
                        .padding(12.dp),
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
        }
    }
}

@Composable
private fun NoraDashboardMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = muted, fontSize = 10.sp, maxLines = 1)
        Text(value, color = light, fontSize = 12.sp, fontWeight = FontWeight.Bold,
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.width(if (compact) 100.dp else 112.dp)
            .clip(shape)
            .background(if (selected) surface2 else surface)
            .then(if (selected) Modifier.border(1.dp, cyan.copy(alpha = 0.75f), shape)
                else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 7.dp else 11.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(flag, fontSize = if (compact) 21.sp else 26.sp, maxLines = 1)
        Text(label, color = if (selected) cyan else light,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoraHeader(painter: Painter, seller: String, connected: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painter = painter, contentDescription = "NoraProxy logo",
            modifier = Modifier.size(59.dp).clip(RoundedCornerShape(18.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text("NoraProxy", color = light, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (seller.isBlank()) "اتصال سریع و ایمن" else "ارائه‌شده توسط " + seller,
                color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(modifier = Modifier.background(if (connected) Color(0xFF1B3D39) else surface2,
            RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(if (connected) "● روشن" else "○ خاموش",
                color = if (connected) green else muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NoraPowerButton(
    connected: Boolean,
    enabled: Boolean,
    diameter: Dp = 272.dp,
    onClick: () -> Unit
) {
    val ring by animateColorAsState(if (connected) green else cyan, label = "ring")
    val glow by animateFloatAsState(if (connected) 1f else .6f, label = "glow")
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(ring.copy(alpha = .09f * glow), radius = radius)
            drawCircle(ring.copy(alpha = .16f), radius = radius * .87f,
                style = Stroke(width = 2.dp.toPx()))
            drawCircle(
                brush = Brush.sweepGradient(listOf(blue, cyan, blue)),
                radius = radius * .76f,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Box(modifier = Modifier.size(diameter * .67f)
            .clip(CircleShape) // Prevent square ripple artifacts on tap.
            .background(
                Brush.radialGradient(listOf(Color(0xFF173C59), Color(0xFF0D2036))),
                CircleShape
            )
            .border(1.dp, Color(0xFF2C6D80), CircleShape)
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
            .background(if (active) surface2 else surface)
            .then(if (active) Modifier.border(1.dp, cyan.copy(alpha = .6f), shape) else Modifier)
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
                .clip(RoundedCornerShape(6.dp)).background(Color(0xFF29435B))) {
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = light, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (caption.isNotBlank()) Text(caption, color = muted, fontSize = 12.sp)
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
    Row(modifier = Modifier.fillMaxWidth()
        .background(if (selected) surface2 else surface, RoundedCornerShape(20.dp))
        .then(if (selected) Modifier.border(1.dp, Color(0xFF1D8EA5),
            RoundedCornerShape(20.dp)) else Modifier)
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(if (compact) 39.dp else 45.dp)
            .background(Color(0xFF1E3C52), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp, color = cyan) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, color = light, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onFavorite != null) {
            Text(if (favorite) "★" else "☆",
                modifier = Modifier.clickable(onClick = onFavorite).padding(6.dp),
                color = if (favorite) cyan else muted,
                fontSize = 21.sp)
        }
        Text(if (delay != null && delay > 0L) persianNumber(delay.toString()) + " ms"
            else if (selected) "—" else "›", fontSize = 13.sp, color = cyan)
    }
}

@Composable
private fun NoraBottomBar(
    active: NoraTab,
    onSelect: (NoraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().background(surface).padding(
        horizontal = 8.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        NoraTab.entries.forEach { tab ->
            Column(modifier = Modifier.weight(1f)
                .clip(RoundedCornerShape(15.dp))
                .clickable { onSelect(tab) }
                .background(if (active == tab) Color(0xFF293A41) else Color.Transparent)
                .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = tab.title,
                    modifier = Modifier.size(24.dp),
                    tint = if (active == tab) cyan else muted
                )
                Text(tab.title, color = if (active == tab) light else muted, fontSize = 10.sp,
                    fontWeight = if (active == tab) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
