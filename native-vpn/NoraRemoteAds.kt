package com.v2ray.ang.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class NoraPromotion(
    val id: String,
    val title: String,
    val target: String?,
    val bitmap: Bitmap
)

/**
 * Public, read-only first-party advertising. No third-party SDK, identifier or
 * profile information is sent. Never fetch a URL from manifest content: only a
 * validated filename under the pinned owner/repository's main branch.
 */
internal object NoraPromotionRepository {
    private const val ROOT =
        "https://raw.githubusercontent.com/hazhanhasani/noraproxy/main/assets/ads/"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val MAX_BANNERS = 4
    private const val MAX_MANIFEST_BYTES = 32 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,72}\\.(?:webp|png|jpg|jpeg)$",
        RegexOption.IGNORE_CASE)

    private fun storage(context: Context): File =
        File(context.cacheDir, "nora_promotions").apply { mkdirs() }

    private fun validFile(file: String): Boolean =
        safeName.matches(file) && !file.contains("..") && !file.contains("/")

    private fun validTarget(raw: String): String? = runCatching {
        val uri = Uri.parse(raw)
        raw.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.encodedUserInfo.isNullOrBlank() &&
                raw.length <= 2048
        }
    }.getOrNull()

    private fun readNetwork(url: String, maxBytes: Int): ByteArray? {
        val connection = (URL(url).openConnection() as? HttpsURLConnection) ?: return null
        return try {
            connection.connectTimeout = 3500
            connection.readTimeout = 4500
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json,image/webp,image/png,image/jpeg")
            if (connection.responseCode != 200) return null
            if (connection.contentLengthLong > maxBytes) return null
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val result = ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    if (result.size() + read > maxBytes) return null
                    result.write(buffer, 0, read)
                }
                result.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        if (bytes.size > MAX_IMAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        }.isFailure) return null
        if (bounds.outWidth !in 64..4096 || bounds.outHeight !in 24..2048) return null
        var sample = 1
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 400) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private data class Descriptor(val id: String, val file: String, val title: String, val target: String?)

    private fun descriptors(json: String): List<Descriptor> {
        val doc = JSONObject(json)
        if (!doc.optBoolean("enabled", true) || doc.optInt("version", 0) != 1) return emptyList()
        val banners: JSONArray = doc.optJSONArray("banners") ?: return emptyList()
        return (0 until banners.length()).asSequence().mapNotNull { i ->
            val item = banners.optJSONObject(i) ?: return@mapNotNull null
            if (!item.optBoolean("enabled", true)) return@mapNotNull null
            val file = item.optString("file").trim()
            if (!validFile(file)) return@mapNotNull null
            val id = item.optString("id", file).take(70)
            val title = item.optString("title", "تبلیغات").take(80)
            Descriptor(id, file, title, validTarget(item.optString("target", "")))
        }.distinctBy { it.file }.take(MAX_BANNERS).toList()
    }

    private fun render(
        context: Context,
        manifest: String,
        allowNetwork: Boolean,
        refreshImages: Boolean = false
    ): List<NoraPromotion> {
        val cache = storage(context)
        val definitions = runCatching { descriptors(manifest) }.getOrElse { emptyList() }
        return definitions.mapNotNull { item ->
            val local = File(cache, item.file)
            val cached = runCatching {
                if (local.isFile && local.length() in 1L..MAX_IMAGE_BYTES.toLong())
                    local.readBytes() else null
            }.getOrNull()
            var bitmap = cached?.let(::decodeScaled)
            if (allowNetwork && (bitmap == null || refreshImages)) {
                val bytes = readNetwork(ROOT + item.file, MAX_IMAGE_BYTES)
                val replacement = bytes?.let(::decodeScaled)
                if (replacement != null) {
                    bitmap = replacement
                    runCatching { local.writeBytes(bytes) }
                }
            }
            bitmap?.let { NoraPromotion(item.id, item.title, item.target, it) }
        }
    }

    /** Intended to be called from Dispatchers.IO, not from a composable draw pass. */
    fun cached(context: Context): List<NoraPromotion> = runCatching {
        val manifest = File(storage(context), "manifest.json")
        if (!manifest.isFile) emptyList() else
            render(context, manifest.readText(Charsets.UTF_8), allowNetwork = false)
    }.getOrDefault(emptyList())

    /** Pull only once every 15 minutes; preserve offline cache on network errors. */
    fun refresh(context: Context): List<NoraPromotion> {
        val prefs = context.getSharedPreferences("nora_ads", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check", 0L)
        val now = SystemClock.elapsedRealtime()
        if (lastCheck in 1..now && now - lastCheck < CACHE_TTL_MS) return cached(context)
        val bytes = readNetwork(ROOT + "manifest.json", MAX_MANIFEST_BYTES)
            ?: return cached(context)
        val json = bytes.toString(Charsets.UTF_8)
        if (runCatching { descriptors(json) }.isFailure) return cached(context)
        val dir = storage(context)
        val manifest = File(dir, "manifest.json")
        val previous = runCatching { manifest.takeIf { it.isFile }?.readText(Charsets.UTF_8) }
            .getOrNull()
        val refreshed = render(context, json, allowNetwork = true, refreshImages = previous != json)
        // Remove outdated image files when campaigns are deleted or renamed.
        val activeFiles = runCatching { descriptors(json).map { it.file }.toSet() }
            .getOrDefault(emptySet())
        dir.listFiles()?.filter {
            it.isFile && it.name != "manifest.json" && it.name !in activeFiles
        }?.forEach { runCatching { it.delete() } }
        runCatching { manifest.writeText(json, Charsets.UTF_8) }
        prefs.edit().putLong("last_check", now).apply()
        return refreshed
    }
}

@Composable
internal fun NoraPromotionsStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var banners by remember { mutableStateOf<List<NoraPromotion>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(context) {
        banners = withContext(Dispatchers.IO) { NoraPromotionRepository.cached(context) }
        val refreshed = withContext(Dispatchers.IO) { NoraPromotionRepository.refresh(context) }
        // An explicitly cleared/disabled manifest must also hide cached banners.
        banners = refreshed
    }
    LaunchedEffect(banners.size) {
        page = 0
        while (banners.size > 1) {
            delay(8000)
            page = (page + 1) % banners.size
        }
    }
    val selected = banners.getOrNull(page)
    val radius = RoundedCornerShape(18.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(81.dp)
                .clip(radius)
                .background(Color(0xFF151B2C))
                .border(1.dp, Color(0xFF282E40), radius)
                .clickable(enabled = selected?.target != null) {
                    selected?.target?.let { destination ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(destination)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                }
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (selected != null) {
                Image(bitmap = remember(selected.bitmap) { selected.bitmap.asImageBitmap() },
                    contentDescription = selected.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✦", fontSize = 22.sp, color = Color(0xFF6F90FF))
                    Column {
                        Text("اطلاع‌رسانی و پیشنهادها", fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F6FA))
                        Text("در این بخش پیشنهادهای جدید نمایش داده می‌شوند",
                            fontSize = 10.sp, color = Color(0xFF9498AB),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (selected != null) {
                Text("تبلیغات", color = Color.White.copy(alpha = .8f), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = .5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        if (banners.size > 1) {
            Row(modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(banners.size) { index ->
                    Box(Modifier.size(5.dp).clip(CircleShape).background(
                        if (index == page) Color(0xFF6F90FF) else Color(0xFF424655)))
                }
            }
        }
    }
}
