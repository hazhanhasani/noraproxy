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
import androidx.compose.foundation.lazy.LazyColumn
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
private val ink = Color(0xFF071323)
private val surface = Color(0xFF10253C)
private val surface2 = Color(0xFF173149)
private val cyan = Color(0xFF1EE2E9)
private val blue = Color(0xFF227DFF)
private val light = Color(0xFFF5FAFF)
private val muted = Color(0xFF9DB6C8)
private val green = Color(0xFF6EE4BD)
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
    var editorVisible by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<SubscriptionCache?>(null) }
    var deletingGroup by remember { mutableStateOf<SubscriptionCache?>(null) }
    var groupBusy by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()
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
    LaunchedEffect(Unit) { while (true) { delay(15000); refreshTick++ } }
    val nodes = remember(refreshTick, appState.selectedGroupId) {
        NoraRouteSelector.load(appState.selectedGroupId)
    }
    val ranked = remember(nodes) { NoraRouteSelector.rank(nodes) }
    val locations = remember(ranked) { NoraRouteSelector.locations(ranked) }
    val identity = remember(nodes) { nodes.joinToString("|") { it.guid } }
    val subscriptions = remember(appState.groups) { mainViewModel.getSubscriptions() }
    var usageByGroup by remember { mutableStateOf<Map<String, NoraSubscriptionUsage>>(emptyMap()) }
    var usageRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(tab, appState.selectedGroupId, subscriptions, usageRefresh) {
        val targets = if (tab == NoraTab.Subscription) subscriptions
            else subscriptions.filter { it.guid == appState.selectedGroupId }
        coroutineScope {
            val limiter = Semaphore(4)
            val updates = targets.take(16).map { group ->
                async(Dispatchers.IO) {
                    group.guid to limiter.withPermit {
                        NoraSubscriptionUsageReader.fetch(group.subscription.url)
                    }
                }
            }.awaitAll().mapNotNull { (id, info) -> info?.let { id to it } }.toMap()
            usageByGroup = usageByGroup.filterKeys { id ->
                subscriptions.any { it.guid == id }
            } + updates
        }
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
                        selectedCountryName = if (selectedCountry == null) "انتخاب هوشمند"
                            else countryTitle,
                        locationFlag = displayedRoute?.flag ?: "🌐",
                        locationSubtitle = if (appState.isRunning) "مسیر فعال: $countryTitle"
                            else countryTitle,
                        locationPing = displayedRoute?.latencyMs,
                        usageText = subscriptions.firstOrNull {
                            it.guid == appState.selectedGroupId
                        }?.let {
                            val details = usageByGroup[it.guid]
                            (details?.remainingTrafficLabel() ?: "حجم نامشخص") + " · " +
                                (details?.remainingTimeLabel() ?: "اعتبار نامشخص")
                        },
                        hasUpdate = updateState.available,
                        onToggle = startOrStop,
                        onChooseLocation = { tab = NoraTab.Locations },
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
                                NoraSection("انتخاب لوکیشن", "فقط بهترین مسیر هر کشور نمایش داده می‌شود.")
                                Spacer(Modifier.height(14.dp))
                                NoraLocationCard("✦", "انتخاب هوشمند", "انتخاب و فعال‌سازی بهترین مسیر شناخته‌شده",
                                    null, selectedCountry == null) {
                                    chooseRoute(ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull(), null)
                                }
                            }
                            items(locations, key = { it.countryCode }) { country ->
                                NoraLocationCard(country.flag, country.countryName,
                                    "مناسب‌ترین سرور این کشور", country.latencyMs,
                                    selectedCountry == country.countryCode &&
                                        activeRoute?.countryCode == country.countryCode) {
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
                                NoraSection("گروه‌های اشتراک", "هر گروه سرورها و مسیر اتصال مستقل دارد.")
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { editingGroup = null; editorVisible = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp)
                                ) { Text("＋ افزودن گروه اشتراک", fontWeight = FontWeight.Bold) }
                            }
                            if (subscriptions.isEmpty()) {
                                item {
                                    Text("هنوز گروهی ساخته نشده است؛ می‌توانید گروه جدید بسازید یا کانفیگ خام را مستقیم وارد کنید.",
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
                                    onClick = { usageRefresh++ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp)
                                ) { Text("بروزرسانی حجم و اعتبار گروه‌ها") }
                            }
                            item {
                                NoraSection("افزودن سرور", "لینک اشتراک یا کانفیگ خام را در گروه انتخاب‌شده وارد کنید.")
                            }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        OutlinedTextField(
                                            value = subscription,
                                            onValueChange = { subscription = it.take(65536) },
                                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                                            label = { Text("لینک اشتراک یا کانفیگ خام (چندخطی)") },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        Button(
                                            onClick = {
                                                val input = subscription.trim()
                                                if (input.isNotEmpty()) {
                                                    onAction(MainAction.ImportBatchConfig(input))
                                                    subscription = ""
                                                }
                                            },
                                            enabled = subscription.isNotBlank() && !loading,
                                            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                                        ) { Text("افزودن اشتراک", fontWeight = FontWeight.Bold) }
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
                                        OutlinedButton(onClick = { onAction(MainAction.UpdateSubscriptions) },
                                            enabled = !loading, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(15.dp)) {
                                            Text("همگام‌سازی گروه انتخاب‌شده")
                                        }
                                        if (loading) CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            item {
                                NoraMetric("سرورهای گروه انتخاب‌شده", nodes.size.toString(), Modifier.fillMaxWidth())
                            }
                        }
                        NoraTab.Settings -> {
                            // Old nora_wordmark.jpg was not decoded by Android on some devices.
                            // Render this banner with Compose text, without risky bitmap decoding.
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(surface, corner)
                                        .padding(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = originalLogo,
                                        contentDescription = "نشان رسمی NoraProxy",
                                        modifier = Modifier.size(76.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("NoraProxy", fontSize = 27.sp,
                                            fontWeight = FontWeight.ExtraBold, color = cyan)
                                        Text("اتصال امن، تجربه‌ای ساده",
                                            color = muted, fontSize = 13.sp)
                                    }
                                }
                            }
                            item { NoraSection("تنظیمات NoraProxy", "نسخه و به‌روزرسانی امن") }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                        Text("به‌روزرسانی مستقیم", fontWeight = FontWeight.Bold, color = light,
                                            fontSize = 18.sp)
                                        Text("APK درون NoraProxy دانلود و پیش از نصب از نظر هش و امضا بررسی می‌شود.",
                                            color = muted, fontSize = 12.sp, lineHeight = 22.sp)
                                        Text(updateState.message, color = cyan, fontSize = 12.sp, lineHeight = 20.sp)
                                        if (updateState.version.isNotEmpty()) {
                                            Text("نسخه موجود: " + updateState.version, color = light)
                                        }
                                        if (updateState.notes.isNotEmpty()) {
                                            Text(updateState.notes.take(600), color = muted, fontSize = 12.sp)
                                        }
                                        if (updateState.busy) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                            Text("دانلود: " + updateState.progress + "%", color = light)
                                        }
                                        Button(onClick = {
                                            if (updateState.downloaded) updater.install()
                                            else if (updateState.available) updater.download()
                                            else updater.check()
                                        }, enabled = !updateState.busy, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp)) {
                                            Text(if (updateState.downloaded) "نصب نسخه دانلودشده"
                                            else if (updateState.available) "دانلود داخل برنامه"
                                            else "بررسی نسخه جدید", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            item {
                                Text("نصب نهایی با تأیید خودتان در صفحه نصب‌کننده اندروید انجام می‌شود. " +
                                    "فایل از مرورگر یا Google Play دانلود نمی‌شود.",
                                    color = muted, fontSize = 12.sp, lineHeight = 22.sp)
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
                    onSave = { name, url, automatic ->
                        groupBusy = true
                        importScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    NoraGroupRepository.save(editingGroup, name, url, automatic)
                                }
                                editorVisible = false
                                onAction(MainAction.RefreshGroups)
                                refreshTick++
                                Toast.makeText(context, "گروه ذخیره شد", Toast.LENGTH_SHORT).show()
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
    locationSubtitle: String,
    locationPing: Long?,
    usageText: String?,
    hasUpdate: Boolean,
    onToggle: () -> Unit,
    onChooseLocation: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(ink, Color(0xFF0B2037), ink))
        ).padding(horizontal = 18.dp)
    ) {
        val compact = maxHeight < 590.dp
        val powerDiameter = (maxHeight * if (compact) .32f else .37f)
            .coerceIn(136.dp, if (compact) 214.dp else 260.dp)
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NoraHeader(logo, seller, connected)
            Spacer(Modifier.weight(1f))
            Text(
                if (waiting) "در حال تغییر وضعیت اتصال..."
                else if (connected) "شما متصل هستید" else "آماده اتصال امن",
                fontSize = if (compact) 21.sp else 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = light,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                if (waiting) "لطفاً چند لحظه منتظر بمانید"
                else if (connected) "اتصال امن NoraProxy فعال است"
                else "با یک لمس به مسیر انتخابی متصل شوید",
                color = muted,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
            NoraPowerButton(
                connected = connected,
                enabled = !waiting && (connected || canConnect),
                diameter = powerDiameter,
                onClick = onToggle
            )
            Text(
                if (waiting) "در حال پردازش..."
                else if (connected) "برای قطع اتصال لمس کنید"
                else if (canConnect) "برای اتصال لمس کنید"
                else "ابتدا اشتراک خود را اضافه کنید",
                color = muted,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.weight(1.1f))
            if (hasUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF143C51))
                        .clickable(onClick = onOpenUpdate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نسخه جدید برای دانلود آماده است",
                        modifier = Modifier.weight(1f),
                        color = cyan, fontSize = 11.sp, maxLines = 1)
                    Text("←", color = light, fontSize = 15.sp)
                }
                Spacer(Modifier.height(9.dp))
            }
            if (usageText != null) {
                Text(usageText, color = muted, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("لوکیشن هوشمند", color = light,
                    modifier = Modifier.weight(1f),
                    fontSize = if (compact) 16.sp else 19.sp,
                    fontWeight = FontWeight.Bold)
                Text("انتخاب لوکیشن  ←", color = cyan, fontSize = 11.sp,
                    modifier = Modifier.clickable(onClick = onChooseLocation))
            }
            Spacer(Modifier.height(9.dp))
            NoraLocationCard(
                emoji = locationFlag,
                title = selectedCountryName,
                subtitle = locationSubtitle,
                delay = locationPing,
                selected = true,
                compact = true,
                onClick = onChooseLocation
            )
        }
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
        Box(modifier = Modifier.background(if (connected) Color(0xFF174D42) else surface2,
            RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(if (connected) "● آنلاین" else "○ آفلاین",
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

@Composable
private fun NoraSection(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = light, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(caption, color = muted, fontSize = 12.sp)
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
    delay: Long?, selected: Boolean, compact: Boolean = false, onClick: () -> Unit
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
        Text(if (selected) "✓" else if (delay != null && delay > 0) delay.toString() + " ms" else "›",
            fontSize = 15.sp, color = cyan)
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
                .background(if (active == tab) Color(0xFF1B4059) else Color.Transparent)
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
