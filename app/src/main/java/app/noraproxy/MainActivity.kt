package app.noraproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import app.noraproxy.core.RankedNode

private val canvas = Color(0xFF10151C)
private val panel = Color(0xFF1C242D)
private val panelLight = Color(0xFF25303A)
private val amber = Color(0xFFFFC078)
private val mint = Color(0xFF80E5BA)
private val textMain = Color(0xFFF3F5F4)
private val textMuted = Color(0xFFA2B0BB)

class MainActivity : ComponentActivity() {
    private lateinit var vm: NoraViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(16, 21, 28)
        window.navigationBarColor = android.graphics.Color.rgb(16, 21, 28)
        vm = ViewModelProvider(this)[NoraViewModel::class.java]
        receiveInvite(intent)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = amber, onPrimary = canvas,
                    secondary = mint, background = canvas,
                    surface = panel, onSurface = textMain
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NoraScreen(
                        vm = vm,
                        copyConfig = ::copyConfig,
                        shareConfig = ::shareConfig
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveInvite(intent)
    }

    private fun receiveInvite(incoming: Intent?) {
        val uri = incoming?.data ?: return
        if (incoming.action != Intent.ACTION_VIEW ||
            uri.scheme != "noraproxy" || uri.host != "setup") return
        vm.prefill(uri.getQueryParameter("url"), uri.getQueryParameter("seller"))
    }

    private fun copyConfig(uri: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NoraProxy server", uri))
        Toast.makeText(this, "کانفیگ کپی شد؛ در V2Box یا v2rayNG وارد کنید.", Toast.LENGTH_LONG).show()
    }

    private fun shareConfig(uri: String) {
        // Official v2rayNG exposes ACTION_SEND text/plain for URI import.
        val direct = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.v2ray.ang")
            putExtra(Intent.EXTRA_TEXT, uri)
        }
        try {
            startActivity(direct)
        } catch (_: android.content.ActivityNotFoundException) {
            // v2rayNG is not installed. Offer any compatible client instead.
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, uri)
            }
            try {
                startActivity(Intent.createChooser(fallback, "انتقال کانفیگ به برنامه VPN"))
            } catch (_: Exception) {
                copyConfig(uri)
            }
        }
    }
}

@Composable
private fun NoraScreen(
    vm: NoraViewModel,
    copyConfig: (String) -> Unit,
    shareConfig: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    val selected = state.selected
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(canvas, Color(0xFF131F24), canvas)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandHeader(state.seller)
        HeroCard(state.reachable, state.regions.size)

        SectionTitle("راه‌اندازی آسان", "لینک اشتراک را از فروشنده خود دریافت کنید")
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = panel)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = state.seller,
                    onValueChange = vm::setSeller,
                    label = { Text("نام فروشنده (اختیاری)") },
                    placeholder = { Text("نام مجموعه") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = state.url,
                    onValueChange = vm::setSubscription,
                    label = { Text("لینک اشتراک HTTPS") },
                    placeholder = { Text("https://example.com/subscription/...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Button(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !state.busy && state.url.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = vm::optimize,
                    colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = canvas)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            color = canvas, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("در حال بررسی " + state.progress + " از " + state.total)
                    } else {
                        Text("⚡  پیدا کردن بهترین سرور", fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    state.notice,
                    color = if (state.error) Color(0xFFFF9D8B) else textMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (state.ranked.isNotEmpty()) {
            SummaryRow(state.ranked.size, state.reachable, state.regions.size)
            SectionTitle("لوکیشن‌های هوشمند", "برای هر کشور فقط بهترین سرور قابل‌دسترس نمایش داده می‌شود")
            state.regions.forEach { ranked ->
                RegionItem(
                    ranked = ranked,
                    selected = ranked.node.id == state.selectedId,
                    onSelect = { vm.choose(ranked.node.id) }
                )
            }
            if (selected != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = panelLight)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("سرور انتخابی", color = textMuted, style = MaterialTheme.typography.labelLarge)
                        Text(
                            selected.node.region.flag + "  " + selected.node.region.title +
                                "   •   " + (selected.stats.latencyMs ?: 0) + " ms TCP",
                            fontSize = 19.sp, fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { copyConfig(selected.node.uri) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mint, contentColor = canvas)
                        ) { Text("کپی کانفیگ منتخب", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { shareConfig(selected.node.uri) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("افزودن به v2rayNG یا ارسال به برنامه دیگر") }
                    }
                }
            }
        }
        Text(
            "آزمون این نسخه فقط مدت اتصال TCP را اندازه می‌گیرد؛ " +
                "سلامت تونل VPN و سرعت واقعی باید پس از واردکردن کانفیگ بررسی شود.",
            color = textMuted,
            fontSize = 11.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun BrandHeader(seller: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(49.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(amber, Color(0xFFFF846A)))),
            contentAlignment = Alignment.Center
        ) {
            Text("N", fontSize = 29.sp, fontWeight = FontWeight.Black, color = canvas)
        }
        Column {
            Text("NoraProxy", fontWeight = FontWeight.ExtraBold, color = textMain, fontSize = 23.sp)
            Text(
                if (seller.isBlank()) "Smart connection companion" else "ارائه‌شده توسط " + seller,
                color = textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeroCard(reachable: Int, regions: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF24443C), Color(0xFF192E37), Color(0xFF28303D))))
            .padding(23.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("اتصال هوشمند، بدون حدس و انتخاب‌های سخت", color = textMain,
            fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 34.sp)
        Text("ما سرورها را بررسی می‌کنیم؛ شما فقط لوکیشن دلخواهتان را انتخاب کنید.",
            color = Color(0xFFCEE4DA), fontSize = 13.sp, lineHeight = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(40.dp), color = Color(0xFF345A4E)) {
                Text("●  " + reachable + " سرور پاسخ‌گو", modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    color = mint, fontSize = 11.sp)
            }
            Text(regions.toString() + " لوکیشن", color = Color(0xFFDFE7E6), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = textMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = textMuted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun SummaryRow(total: Int, reachable: Int, regions: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryPill(total.toString(), "بررسی‌شده", Modifier.weight(1f))
        SummaryPill(reachable.toString(), "پاسخ‌گو", Modifier.weight(1f))
        SummaryPill(regions.toString(), "لوکیشن", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryPill(number: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = panel),
        shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, color = mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RegionItem(ranked: RankedNode, selected: Boolean, onSelect: () -> Unit) {
    val node = ranked.node
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF294338) else panel),
        border = if (selected) BorderStroke(1.dp, mint) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(49.dp).clip(RoundedCornerShape(14.dp)).background(panelLight),
                contentAlignment = Alignment.Center) {
                Text(node.region.flag, fontSize = 26.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(node.region.title, color = textMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("بهترین مسیر از میان کانفیگ‌های این لوکیشن",
                    color = textMuted, fontSize = 10.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((ranked.stats.latencyMs ?: 0).toString() + " ms",
                    color = mint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (selected) "انتخاب‌شده ✓" else "TCP", color = textMuted, fontSize = 11.sp)
            }
        }
    }
}
