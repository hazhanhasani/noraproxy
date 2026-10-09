package com.v2ray.ang.ui.main

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import kotlinx.coroutines.delay

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
    val appState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val loading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val updater = remember(context.applicationContext) { NoraUpdater(context.applicationContext) }
    val updateState by updater.state.collectAsStateWithLifecycle()
    DisposableEffect(updater) { onDispose { updater.dispose() } }
    LaunchedEffect(updater) { updater.check() }
    var tab by remember { mutableStateOf(NoraTab.Home) }
    var subscription by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(appState.groups, appState.isTesting, loading) { refreshTick++ }
    LaunchedEffect(Unit) { while (true) { delay(15000); refreshTick++ } }
    val nodes = remember(refreshTick) { NoraRouteSelector.load() }
    val ranked = remember(nodes) { NoraRouteSelector.rank(nodes) }
    val locations = remember(ranked) { NoraRouteSelector.locations(ranked) }
    val identity = remember(nodes) { nodes.joinToString("|") { it.guid } }
    LaunchedEffect(identity, loading) {
        if (!loading && nodes.isNotEmpty() && nodes.all { it.latencyMs == 0L } &&
            !appState.isTesting) {
            onAction(MainAction.TestRealAllServers)
        }
    }
    val selected = if (selectedCountry == null) {
        ranked.firstOrNull { it.latencyMs > 0 } ?: ranked.firstOrNull()
    } else locations.firstOrNull { it.countryCode == selectedCountry }
    val countryTitle = selected?.let { it.flag + "  " + it.countryName } ?: "هنوز اشتراکی ندارید"
    val brand = remember(context) {
        context.getSharedPreferences("nora_brand", android.content.Context.MODE_PRIVATE)
            .getString("seller", "").orEmpty()
    }
    val originalLogo = painterResource(R.drawable.nora_launcher)

    val startOrStop: () -> Unit = {
        if (appState.isRunning) {
            onAction(MainAction.ToggleService)
        } else {
            selected?.let { route ->
                if (appState.selectedGuid != route.guid) onAction(MainAction.SelectServer(route.guid))
                // Permission-aware original MainActivity -> native CoreVpnService.
                onAction(MainAction.ToggleService)
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
                        NoraTab.Home -> {
                            item {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(if (appState.isRunning) "شما متصل هستید" else "آماده اتصال امن",
                                        fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = light)
                                    Text(if (appState.isRunning) "اتصال توسط خود NoraProxy برقرار است."
                                    else "تنها با یک لمس به مسیر هوشمند متصل شوید.",
                                        fontSize = 12.sp, color = muted, textAlign = TextAlign.Center)
                                    NoraPowerButton(
                                        connected = appState.isRunning,
                                        enabled = selected != null || appState.isRunning,
                                        onClick = startOrStop
                                    )
                                    Text(if (appState.isRunning) "برای قطع اتصال لمس کنید"
                                    else "برای اتصال لمس کنید", color = muted, fontSize = 12.sp)
                                }
                            }
                            if (updateState.available) {
                                item {
                                    Row(modifier = Modifier.fillMaxWidth()
                                        .background(Color(0xFF143C51), RoundedCornerShape(18.dp))
                                        .clickable { tab = NoraTab.Settings }.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("نسخه جدید NoraProxy آماده دانلود است.", color = cyan,
                                            modifier = Modifier.weight(1f), fontSize = 12.sp)
                                        Text("←", color = light)
                                    }
                                }
                            }
                            item {
                                NoraSection("Smart Location", "بهترین انتخاب متناسب با شبکه شما")
                                Spacer(Modifier.height(11.dp))
                                NoraLocationCard(
                                    emoji = selected?.flag ?: "🌐",
                                    title = if (selectedCountry == null) "انتخاب هوشمند" else countryTitle,
                                    subtitle = countryTitle,
                                    delay = selected?.latencyMs,
                                    selected = true,
                                    onClick = { tab = NoraTab.Locations }
                                )
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    NoraMetric("سرورهای قابل انتخاب", nodes.size.toString(),
                                        Modifier.weight(1f))
                                    NoraMetric("لوکیشن‌ها", locations.size.toString(),
                                        Modifier.weight(1f))
                                }
                            }
                            item {
                                OutlinedButton(onClick = { onAction(MainAction.TestRealAllServers) },
                                    enabled = !appState.isTesting && nodes.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(17.dp)) {
                                    Text(if (appState.isTesting) "در حال بررسی مسیرها..."
                                    else "↻  سنجش پینگ واقعی سرورها")
                                }
                            }
                        }
                        NoraTab.Locations -> {
                            item {
                                NoraSection("انتخاب لوکیشن", "فقط بهترین مسیر هر کشور نمایش داده می‌شود.")
                                Spacer(Modifier.height(14.dp))
                                NoraLocationCard("✦", "Smart Location", "انتخاب خودکار سریع‌ترین مسیر شناخته‌شده",
                                    null, selectedCountry == null) { selectedCountry = null }
                            }
                            items(locations, key = { it.countryCode }) { country ->
                                NoraLocationCard(country.flag, country.countryName,
                                    "مناسب‌ترین سرور این کشور", country.latencyMs,
                                    selectedCountry == country.countryCode) {
                                    selectedCountry = country.countryCode
                                    tab = NoraTab.Home
                                }
                            }
                            item {
                                Text("تأخیرها آخرین نتایج اندازه‌گیری هستند؛ برای نتیجه دقیق‌تر پس از تغییر شبکه دوباره تست بگیرید.",
                                    fontSize = 12.sp, lineHeight = 21.sp, color = muted)
                            }
                        }
                        NoraTab.Subscription -> {
                            item { NoraSection("اشتراک من", "اشتراک اختصاصی فروشنده را اینجا اضافه کنید.") }
                            item {
                                Card(shape = corner, colors = CardDefaults.cardColors(containerColor = surface)) {
                                    Column(modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        OutlinedTextField(
                                            value = subscription,
                                            onValueChange = { subscription = it.take(4096) },
                                            modifier = Modifier.fillMaxWidth(), maxLines = 3,
                                            label = { Text("لینک HTTPS یا کانفیگ") },
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
                                        OutlinedButton(onClick = { onAction(MainAction.UpdateSubscriptions) },
                                            enabled = !loading, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(15.dp)) {
                                            Text("همگام‌سازی سرورهای جدید")
                                        }
                                        if (loading) CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            item {
                                NoraMetric("کانفیگ‌های این اشتراک", nodes.size.toString(), Modifier.fillMaxWidth())
                            }
                        }
                        NoraTab.Settings -> {
                            // Old nora_wordmark.jpg was not decoded by Android on some devices.
                            // Render this banner with Compose text, without risky bitmap decoding.
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(surface, corner)
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("NoraProxy", fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold, color = cyan)
                                    Text("اتصال امن، تجربه‌ای ساده", color = muted,
                                        fontSize = 13.sp)
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
            Text(if (seller.isBlank()) "Your secure connection" else "ارائه‌شده توسط " + seller,
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
private fun NoraPowerButton(connected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (connected) green else cyan, label = "ring")
    val glow by animateFloatAsState(if (connected) 1f else .6f, label = "glow")
    Box(modifier = Modifier.size(272.dp), contentAlignment = Alignment.Center) {
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
        Box(modifier = Modifier.size(182.dp)
            .background(
                Brush.radialGradient(listOf(Color(0xFF173C59), Color(0xFF0D2036))),
                CircleShape
            )
            .border(1.dp, Color(0xFF2C6D80), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(75.dp)) {
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
    delay: Long?, selected: Boolean, onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()
        .background(if (selected) surface2 else surface, RoundedCornerShape(20.dp))
        .then(if (selected) Modifier.border(1.dp, Color(0xFF1D8EA5),
            RoundedCornerShape(20.dp)) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(45.dp)
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
