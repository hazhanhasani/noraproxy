package com.v2ray.ang.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Display only bounded, redacted diagnostic output. No network transport and
 * never copy the raw logcat buffer automatically. Logs can still contain
 * sensitive data, so the screen explains that masking is best effort.
 */
internal object NoraDiagnosticReader {
    private val config = Regex("""(?i)\b(?:vmess|vless|trojan|ss|ssr|socks5?|hy2|hysteria2|tuic|wireguard)://[^\s<>]+""")
    private val url = Regex("""(?i)\b(?:https?|wss?)://[^\s<>]+""")
    private val bearer = Regex("""(?i)\bBearer\s+\S+""")
    private val credential = Regex("""(?i)\b(token|password|passwd|secret|authorization|api[_-]?key)\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s,;]+)""")
    private val longSecret = Regex("""\b[A-Za-z0-9_+/=-]{64,}\b""")

    fun sanitize(line: String): String {
        var safe = line.take(1800).filter { it == '\t' || it.code >= 32 && it.code != 127 }
        safe = config.replace(safe, "[REDACTED-CONFIG]")
        safe = url.replace(safe, "[REDACTED-URL]")
        safe = bearer.replace(safe, "Bearer [REDACTED]")
        safe = credential.replace(safe) { "${it.groupValues[1]}=[REDACTED]" }
        safe = longSecret.replace(safe, "[REDACTED-KEY]")
        return safe
    }

    fun read(): List<String> {
        // Same logcat facility as the pinned upstream log viewer; it can read
        // only the logs permitted to this Android application.
        val process = ProcessBuilder(
            "logcat", "-d", "-t", "300", "-v", "time", "-s",
            "GoLog,${AppConfig.ANG_PACKAGE},AndroidRuntime,System.err"
        ).redirectErrorStream(true).start()
        return try {
            val rows = mutableListOf<String>()
            process.inputStream.bufferedReader().use { reader ->
                while (rows.size < 300) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) rows.add(sanitize(line))
                }
            }
            rows.asReversed().take(250)
        } finally {
            process.destroy()
        }
    }
}

@Composable
internal fun NoraLogsScreen(onOpenFullLogcat: () -> Unit) {
    val colors = LocalNoraPalette.current
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(refresh) {
        busy = true
        error = false
        val result = withContext(Dispatchers.IO) {
            runCatching { NoraDiagnosticReader.read() }
        }
        result.onSuccess { lines = it }.onFailure {
            lines = emptyList()
            error = true
        }
        busy = false
    }
    val filtered = remember(lines, search) {
        if (search.isBlank()) lines else lines.filter {
            it.contains(search.trim(), ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("گزارش عیب‌یابی اتصال", color = colors.light,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "فقط آخرین گزارش‌ها نمایش داده می‌شوند. لینک‌ها و کلیدهای شناخته‌شده پوشانده می‌شوند، اما این پوشش تضمین حذف همه اطلاعات حساس نیست.",
                        color = colors.muted, fontSize = 12.sp, lineHeight = 20.sp
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it.take(120) },
                        singleLine = true,
                        label = { Text("جست‌وجو در گزارش") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refresh++ },
                            modifier = Modifier.weight(1f), enabled = !busy) {
                            Text("تازه‌سازی", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                Utils.setClipboard(context, filtered.joinToString("\n"))
                                Toast.makeText(context, "گزارش پالایش‌شده کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && filtered.isNotEmpty()
                        ) { Text("کپی پالایش‌شده", fontSize = 12.sp) }
                    }
                    OutlinedButton(onClick = onOpenFullLogcat, modifier = Modifier.fillMaxWidth()) {
                        Text("نمایش گزارش کامل در ابزار Xray")
                    }
                }
            }
        }
        if (busy) item { CircularProgressIndicator(color = colors.cyan) }
        if (error) item {
            Text("خواندن گزارش ممکن نیست. می‌توانید از ابزار اصلی Xray استفاده کنید.",
                color = colors.error, fontSize = 12.sp)
        }
        if (!busy && !error && filtered.isEmpty()) item {
            Text("گزارشی برای نمایش پیدا نشد.", color = colors.muted, fontSize = 12.sp)
        }
        itemsIndexed(filtered, key = { index, _ -> index }) { _, item ->
            Text(
                item,
                color = colors.light,
                fontSize = 11.sp,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, colors.edge, RoundedCornerShape(12.dp))
                    .padding(11.dp)
            )
        }
    }
}

@Composable
internal fun NoraBackupHubScreen(onOpenNativeBackup: () -> Unit) {
    val colors = LocalNoraPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("پشتیبان‌گیری و بازیابی امن", color = colors.light,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "از این بخش به ابزار اصلی پشتیبان‌گیری Xray منتقل می‌شوید؛ همان ابزار که فایل ZIP، انتخاب مقصد و فرایند بازیابی را مدیریت می‌کند.",
                        color = colors.muted, fontSize = 12.sp, lineHeight = 21.sp
                    )
                    Text(
                        "هشدار: فایل پشتیبان ممکن است شامل کانفیگ‌ها، نشانی اشتراک و اطلاعات محرمانه باشد. فقط در محل امن نگهداری کنید و فایل ناشناس را بازیابی نکنید.",
                        color = colors.warningText, fontSize = 12.sp, lineHeight = 21.sp,
                        modifier = Modifier.fillMaxWidth()
                            .background(colors.warningBackground, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenNativeBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بازکردن پشتیبان‌گیری و بازیابی") }
                    Text(
                        "برای جلوگیری از آسیب به اشتراک‌های موجود، در این نسخه فرمت فایل و منطق بازگردانی تغییر نکرده است.",
                        color = colors.muted, fontSize = 11.sp, lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
