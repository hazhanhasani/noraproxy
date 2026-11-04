package com.v2ray.ang.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NoraProxy-only advanced interface. This is a Compose presentation over the existing
 * pinned Xray / MMKV settings, not an additional VPN engine or settings database.
 */
internal enum class NoraAdvancedPage(val label: String, val hint: String) {
    Apps("مدیریت برنامه‌ها", "انتخاب برنامه‌های متصل یا مستثنا از VPN"),
    Routing("قوانین مسیر‌یابی", "اولویت قواعد و کنترل مسیر ترافیک"),
    Network("شبکه و DNS", "تنظیمات اصلی DNS و پشته شبکه")
}

private val noraPanel = Color(0xFF121319)
private val noraPanelInner = Color(0xFF181A22)
private val noraBorder = Color(0xFF293147)
private val noraAccent = Color(0xFF4B7DFF)
private val noraWhite = Color(0xFFF7F9FF)
private val noraSecondary = Color(0xFFACB8C9)

@Composable
internal fun NoraAdvancedScreen(
    page: NoraAdvancedPage,
    vpnRunning: Boolean,
    onBack: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF08090E))
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp)
                    .background(noraPanelInner, RoundedCornerShape(15.dp))
                    .border(1.dp, noraBorder, RoundedCornerShape(15.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("→", color = noraWhite, fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(page.label, color = noraWhite, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold)
                Text(page.hint, color = noraSecondary, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }

        when (page) {
            NoraAdvancedPage.Apps -> NoraAdvancedApps(vpnRunning)
            NoraAdvancedPage.Routing -> NoraAdvancedRouting(vpnRunning, onNavigate)
            NoraAdvancedPage.Network -> NoraAdvancedNetwork(vpnRunning, onNavigate)
        }
    }
}

@Composable
private fun NoraAdvancedCard(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, noraBorder, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = noraPanel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = noraWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (!description.isNullOrBlank()) {
                Text(description, color = noraSecondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
            content()
        }
    }
}

@Composable
private fun NoraAdvancedSwitch(
    title: String,
    summary: String,
    value: Boolean,
    enabled: Boolean = true,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(
                value = value,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChanged
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) noraWhite else noraSecondary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = noraSecondary, fontSize = 11.sp, lineHeight = 18.sp)
        }
        Switch(checked = value, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
private fun NoraAdvancedNotice(vpnRunning: Boolean) {
    if (vpnRunning) {
        Text(
            "اتصال فعلی فعال است. برای اعمال قطعی تغییرات مسیریابی یا DNS، اتصال را یک‌بار قطع و وصل کنید.",
            color = Color(0xFFFFD18A), fontSize = 12.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF30281C), RoundedCornerShape(13.dp))
                .padding(12.dp)
        )
    }
}

@Composable
private fun NoraAdvancedApps(vpnRunning: Boolean) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet() ?: emptySet())
    }
    var enabled by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false))
    }
    var bypass by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false))
    }
    LaunchedEffect(Unit) {
        isLoading = true
        loadError = false
        try {
            list = AppManagerUtil.loadNetworkAppList(context.applicationContext)
                .sortedWith(compareBy<AppInfo> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() })
        } catch (_: Exception) {
            loadError = true
        } finally {
            isLoading = false
        }
    }

    val shown = remember(list, query, selectedOnly, selected) {
        list.filter {
            (!selectedOnly || it.packageName in selected) &&
                (query.isBlank() || it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true))
        }
    }
    fun saveSelected(next: Set<String>) {
        selected = next
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, next.toMutableSet())
        SettingsChangeManager.makeRestartService()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { NoraAdvancedNotice(vpnRunning) }
        item {
            NoraAdvancedCard(
                title = "Split Tunneling",
                description = "انتخاب کنید کدام برنامه‌ها از تونل VPN استفاده کنند."
            ) {
                NoraAdvancedSwitch(
                    title = "فعال‌سازی مدیریت برنامه‌ها",
                    summary = "با خاموش بودن این گزینه، تنظیمات انتخاب برنامه اعمال نمی‌شوند.",
                    value = enabled,
                    onChanged = { next ->
                        if (next && selected.isEmpty()) {
                            Toast.makeText(context,
                                "ابتدا حداقل یک برنامه را انتخاب کنید", Toast.LENGTH_SHORT).show()
                        } else {
                            enabled = next
                            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, next)
                            SettingsChangeManager.makeRestartService()
                        }
                    }
                )
                NoraAdvancedSwitch(
                    title = "برنامه‌های انتخاب‌شده مستثنا شوند",
                    summary = if (bypass) "برنامه‌های انتخاب‌شده مستقیم وصل می‌شوند" else
                        "فقط برنامه‌های انتخاب‌شده از VPN عبور می‌کنند",
                    value = bypass,
                    onChanged = { next ->
                        if (!next && enabled && selected.isEmpty()) {
                            Toast.makeText(context,
                                "پیش از انتخاب حالت محدود، حداقل یک برنامه انتخاب کنید",
                                Toast.LENGTH_SHORT).show()
                        } else {
                            bypass = next
                            MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, next)
                            SettingsChangeManager.makeRestartService()
                        }
                    }
                )
                Text(
                    "تعداد انتخاب‌شده: " + persianNumber(selected.size.toString()),
                    color = noraAccent, fontSize = 12.sp
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                label = { Text("جست‌وجوی نام برنامه یا Package") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !selectedOnly,
                    onClick = { selectedOnly = false },
                    label = { Text("همه برنامه‌ها") }
                )
                FilterChip(
                    selected = selectedOnly,
                    onClick = { selectedOnly = true },
                    label = { Text("انتخاب‌شده‌ها") }
                )
            }
        }
        when {
            isLoading -> item {
                Text("در حال بارگذاری برنامه‌ها…", color = noraSecondary, fontSize = 13.sp)
            }
            loadError -> item {
                Text("فهرست برنامه‌ها در دسترس نیست؛ دوباره وارد این بخش شوید.",
                    color = Color(0xFFFFB6A8), fontSize = 13.sp)
            }
            shown.isEmpty() -> item {
                Text("برنامه‌ای با این فیلتر پیدا نشد.", color = noraSecondary, fontSize = 13.sp)
            }
            else -> items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(noraPanel, RoundedCornerShape(15.dp))
                        .border(1.dp, noraBorder, RoundedCornerShape(15.dp))
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = {
                                val next = if (checked) selected - app.packageName
                                    else selected + app.packageName
                                // Never silently turn a zero-app allowlist into a blocked tunnel.
                                if (enabled && !bypass && next.isEmpty()) {
                                    Toast.makeText(context,
                                        "برای حالت «فقط برنامه‌های انتخاب‌شده» یک برنامه لازم است",
                                        Toast.LENGTH_SHORT).show()
                                } else saveSelected(next)
                            }
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = noraWhite, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, color = noraSecondary, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun NoraAdvancedRouting(
    vpnRunning: Boolean,
    onNavigate: (MainDestination) -> Unit
) {
    var rules by remember { mutableStateOf<List<RulesetItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var showEnabledOnly by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val availableStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    var strategy by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: "AsIs")
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                rules = MmkvManager.decodeRoutingRulesets()?.toList() ?: emptyList()
                strategy = MmkvManager.decodeSettingsString(
                    AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: "AsIs"
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        rules = withContext(Dispatchers.IO) {
            MmkvManager.decodeRoutingRulesets()?.toList() ?: emptyList()
        }
        loaded = true
    }
    val displayed = remember(rules, showEnabledOnly) {
        rules.mapIndexed { index, rule -> index to rule }
            .filter { !showEnabledOnly || it.second.enabled }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { NoraAdvancedNotice(vpnRunning) }
        item {
            NoraAdvancedCard(
                "راهبرد دامنه",
                "برای جلوگیری از خطای DNS، حالت فعلی حفظ می‌شود مگر اینکه خودتان آن را تغییر دهید."
            ) {
                availableStrategies.forEach { value ->
                    NoraAdvancedSwitch(
                        title = value,
                        summary = if (strategy == value) "حالت فعلی" else "انتخاب این حالت",
                        value = strategy == value,
                        onChanged = { checked ->
                            if (checked && strategy != value) {
                                strategy = value
                                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                                SettingsChangeManager.makeRestartService()
                            }
                        }
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("قوانین موجود", color = noraWhite,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(persianNumber(rules.size.toString()) + " قانون",
                        color = noraSecondary, fontSize = 11.sp)
                }
                FilterChip(
                    selected = showEnabledOnly,
                    onClick = { showEnabledOnly = !showEnabledOnly },
                    label = { Text("فقط فعال‌ها") }
                )
            }
        }
        if (!loaded) item {
            Text("در حال بارگذاری قوانین…", color = noraSecondary)
        }
        if (loaded && displayed.isEmpty()) item {
            NoraAdvancedCard("قانونی برای نمایش وجود ندارد",
                "برای ساخت قانون جدید، گزینه افزودن را انتخاب کنید.") {}
        }
        item {
            OutlinedButton(
                onClick = { editingIndex = -1 },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) { Text("＋ افزودن قانون جدید") }
        }
        items(displayed.size, key = { index ->
            val (position, rule) = displayed[index]
            rule.id.ifBlank { "rule-$position" }
        }) { displayIndex ->
            val (position, rule) = displayed[displayIndex]
            NoraAdvancedCard(
                title = rule.remarks?.ifBlank { "قانون بدون نام" } ?: "قانون بدون نام",
                description = buildString {
                    append("خروجی: ")
                    append(rule.outboundTag.ifBlank { "پیش‌فرض" })
                    val info = rule.domain ?: rule.ip ?: rule.process
                    if (!info.isNullOrEmpty()) {
                        append(" · ")
                        append(info.take(2).joinToString("، ").take(120))
                    }
                }
            ) {
                NoraAdvancedSwitch(
                    title = "قانون فعال",
                    summary = if (rule.locked == true) "این قانون محافظت‌شده است" else
                        "اولویت قوانین مطابق ترتیب فهرست اصلی باقی می‌ماند",
                    value = rule.enabled,
                    enabled = rule.locked != true,
                    onChanged = { next ->
                        val updated = rule.copy(enabled = next)
                        SettingsManager.saveRoutingRuleset(position, updated)
                        rules = rules.toMutableList().also { it[position] = updated }
                        SettingsChangeManager.makeRestartService()
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = rule.locked != true,
                        onClick = { editingIndex = position }
                    ) { Text("ویرایش", fontSize = 11.sp) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !showEnabledOnly && position > 0 &&
                            rule.locked != true && rules[position - 1].locked != true,
                        onClick = {
                            val updated = rules.toMutableList()
                            val entry = updated.removeAt(position)
                            updated.add(position - 1, entry)
                            MmkvManager.encodeRoutingRulesets(updated)
                            rules = updated.toList()
                            SettingsChangeManager.makeRestartService()
                        }
                    ) { Text("بالاتر", fontSize = 11.sp) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !showEnabledOnly && position < rules.lastIndex &&
                            rule.locked != true && rules[position + 1].locked != true,
                        onClick = {
                            val updated = rules.toMutableList()
                            val entry = updated.removeAt(position)
                            updated.add(position + 1, entry)
                            MmkvManager.encodeRoutingRulesets(updated)
                            rules = updated.toList()
                            SettingsChangeManager.makeRestartService()
                        }
                    ) { Text("پایین‌تر", fontSize = 11.sp) }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onNavigate(MainDestination.Routing) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) { Text("ابزارهای پیشرفته و Import/Export قوانین") }
        }
    }
    editingIndex?.let { position ->
        val current = if (position < 0) null else rules.getOrNull(position)
        // Rules may have been removed by an external editor while this page was visible.
        if (position >= 0 && current == null) {
            editingIndex = null
        } else if (current?.locked != true) {
            NoraRoutingRuleEditor(
                current = current,
                onDismiss = { editingIndex = null },
                onSave = { rule ->
                    try {
                        val latest = MmkvManager.decodeRoutingRulesets()?.toList() ?: emptyList()
                        if (position >= 0 && (position !in latest.indices ||
                                latest[position].id != current?.id ||
                                latest[position].locked == true)) {
                            rules = latest
                            editingIndex = null
                            Toast.makeText(context, "قانون تغییر کرده؛ دوباره آن را باز کنید",
                                Toast.LENGTH_LONG).show()
                        } else {
                            SettingsManager.saveRoutingRuleset(position, rule)
                            rules = MmkvManager.decodeRoutingRulesets()?.toList() ?: emptyList()
                            SettingsChangeManager.makeRestartService()
                            editingIndex = null
                            Toast.makeText(context, "قانون ذخیره شد", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "ذخیره قانون انجام نشد", Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = if (position < 0) null else ({
                    try {
                        val saved = MmkvManager.decodeRoutingRulesets()
                        if (saved != null && position in saved.indices &&
                            saved[position].locked != true && saved[position].id == current?.id) {
                            SettingsManager.removeRoutingRuleset(position)
                            rules = MmkvManager.decodeRoutingRulesets()?.toList() ?: emptyList()
                            SettingsChangeManager.makeRestartService()
                            Toast.makeText(context, "قانون حذف شد", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "قانون تغییر کرده؛ فهرست را بررسی کنید",
                                Toast.LENGTH_LONG).show()
                            rules = saved?.toList() ?: emptyList()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "حذف قانون انجام نشد", Toast.LENGTH_LONG).show()
                    } finally {
                        editingIndex = null
                    }
                })
            )
        }
    }
}

@Composable
private fun NoraAdvancedNetwork(
    vpnRunning: Boolean,
    onNavigate: (MainDestination) -> Unit
) {
    val context = LocalContext.current
    var localDns by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false))
    }
    var fakeDns by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED, false))
    }
    var ipv6 by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, false))
    }
    var preferIpv6 by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6, false))
    }
    var sniffing by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true))
    }
    var routeOnly by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false))
    }
    var showDnsEditor by remember { mutableStateOf(false) }
    var remoteDns by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY) ?: AppConfig.DNS_PROXY)
    }
    var directDns by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT) ?: AppConfig.DNS_DIRECT)
    }
    var vpnDns by remember {
        mutableStateOf(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN) ?: AppConfig.DNS_VPN)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                localDns = MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, false)
                fakeDns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED, false)
                ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, false)
                preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6, false)
                sniffing = MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true)
                routeOnly = MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
                remoteDns = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY) ?: AppConfig.DNS_PROXY
                directDns = MmkvManager.decodeSettingsString(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT) ?: AppConfig.DNS_DIRECT
                vpnDns = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN) ?: AppConfig.DNS_VPN
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun save(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        SettingsChangeManager.notifySettingChanged(key)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { NoraAdvancedNotice(vpnRunning) }
        item {
            NoraAdvancedCard("DNS", "تغییر تنظیمات DNS ممکن است نتیجه مسیریابی دامنه‌ها را تغییر دهد.") {
                NoraAdvancedSwitch("DNS محلی",
                    "فعال‌سازی روش DNS محلی هسته Xray",
                    localDns, onChanged = { localDns = it; save(AppConfig.PREF_LOCAL_DNS_ENABLED, it) })
                NoraAdvancedSwitch("Fake DNS",
                    "مناسب برخی تنظیمات TUN؛ فقط در صورت سازگاری با سرور فعال کنید.",
                    fakeDns, onChanged = { fakeDns = it; save(AppConfig.PREF_FAKE_DNS_ENABLED, it) })
            }
        }
        item {
            NoraAdvancedCard("پروتکل اینترنت", "گزینه‌های IPv6 روی برخی اپراتورها نیازمند تست عملی‌اند.") {
                NoraAdvancedSwitch("IPv6",
                    "اجازه مسیریابی IPv6 در تونل",
                    ipv6, onChanged = { ipv6 = it; save(AppConfig.PREF_IPV6_ENABLED, it) })
                NoraAdvancedSwitch("اولویت IPv6",
                    "در صورت فعال بودن IPv6، آن را در اولویت قرار دهید.",
                    preferIpv6, enabled = ipv6,
                    onChanged = { preferIpv6 = it; save(AppConfig.PREF_PREFER_IPV6, it) })
            }
        }
        item {
            NoraAdvancedCard("تشخیص ترافیک",
                "این تنظیمات مستقیماً روی پیکربندی داخلی Xray تأثیر می‌گذارند.") {
                NoraAdvancedSwitch("تشخیص پروتکل (Sniffing)",
                    "شناسایی مقصد ترافیک برای قواعد مسیریابی",
                    sniffing, onChanged = { sniffing = it; save(AppConfig.PREF_SNIFFING_ENABLED, it) })
                NoraAdvancedSwitch("فقط مسیریابی (Route Only)",
                    "از مقصد شناسایی‌شده برای تصمیم مسیریابی استفاده کنید.",
                    routeOnly, enabled = sniffing,
                    onChanged = { routeOnly = it; save(AppConfig.PREF_ROUTE_ONLY_ENABLED, it) })
            }
        }
        item {
            NoraAdvancedCard("سرورهای DNS", "برای هر مسیر می‌توانید DNS اختصاصی تعیین کنید.") {
                OutlinedButton(
                    onClick = { showDnsEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("ویرایش DNS پروکسی، مستقیم و تونل") }
            }
        }
        item {
            OutlinedButton(
                onClick = { onNavigate(MainDestination.Settings) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) { Text("MTU و سایر تنظیمات تخصصی Xray") }
        }
        item {
            Text(
                "NoraProxy از تنظیمات موجود Xray استفاده می‌کند. این صفحه هسته اتصال جدیدی اجرا نمی‌کند.",
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                color = noraSecondary, fontSize = 11.sp, lineHeight = 20.sp
            )
        }
    }
    if (showDnsEditor) {
        NoraDnsServersEditor(
            currentRemote = remoteDns,
            currentDirect = directDns,
            currentVpn = vpnDns,
            onDismiss = { showDnsEditor = false },
            onSave = { remote, direct, vpn ->
                if (listOf(
                        NoraAdvancedValidation.validateDnsCsv(remote, false),
                        NoraAdvancedValidation.validateDnsCsv(direct, false),
                        NoraAdvancedValidation.validateDnsCsv(vpn, true)
                    ).any { it != null }) {
                    Toast.makeText(context, "آدرس DNS نامعتبر است", Toast.LENGTH_LONG).show()
                } else {
                    val resolvedRemote = NoraAdvancedValidation.normalizedDnsCsv(remote).ifBlank { AppConfig.DNS_PROXY }
                    val resolvedDirect = NoraAdvancedValidation.normalizedDnsCsv(direct).ifBlank { AppConfig.DNS_DIRECT }
                    val resolvedVpn = NoraAdvancedValidation.normalizedDnsCsv(vpn).ifBlank { AppConfig.DNS_VPN }
                    val changes = listOf(
                        AppConfig.PREF_REMOTE_DNS to resolvedRemote,
                        AppConfig.PREF_DOMESTIC_DNS to resolvedDirect,
                        AppConfig.PREF_VPN_DNS to resolvedVpn
                    )
                    var updated = false
                    changes.forEach { (key, value) ->
                        if (MmkvManager.decodeSettingsString(key) != value) {
                            MmkvManager.encodeSettings(key, value)
                            updated = true
                        }
                    }
                    if (updated) SettingsChangeManager.makeRestartService()
                    remoteDns = resolvedRemote
                    directDns = resolvedDirect
                    vpnDns = resolvedVpn
                    showDnsEditor = false
                    Toast.makeText(context, "DNS ذخیره شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
