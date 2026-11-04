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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val bitmap: Bitmap,
    val loadedRevision: String?
)

/**
 * Public, read-only first-party advertising. No third-party SDK, identifier or
 * profile information is sent. Never fetch a URL from manifest content: only a
 * validated filename under the pinned owner/repository's main branch.
 */
internal object NoraPromotionRepository {
    private const val ROOT =
        "https://raw.githubusercontent.com/hazhanhasani/noraproxy/main/assets/ads/"
    private const val CACHE_TTL_MS = 15_000L
    private const val MAX_BANNERS = 4
    private const val MAX_MANIFEST_BYTES = 32 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,72}\\.(?:webp|png|jpg|jpeg)$",
        RegexOption.IGNORE_CASE)
    private val safeRevision = Regex("^[a-fA-F0-9]{40}$")

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
        // DNS failures and offline devices can throw before an HTTP connection exists.
        val connection = try {
            URL(url).openConnection() as? HttpsURLConnection ?: return null
        } catch (_: Exception) {
            return null
        }
        return try {
            connection.connectTimeout = 3500
            connection.readTimeout = 4500
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
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

    private data class Descriptor(
        val id: String,
        val file: String,
        val title: String,
        val target: String?,
        val revision: String?
    )

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
            val revision = item.optString("sha").takeIf { safeRevision.matches(it) }
            Descriptor(id, file, title, validTarget(item.optString("target", "")), revision)
        }.distinctBy { it.file }.take(MAX_BANNERS).toList()
    }

    private fun render(
        context: Context,
        manifest: String,
        allowNetwork: Boolean
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
            // The manifest contains the SHA-1 of Git's immutable image blob.
            // A replacement with the SAME filename therefore invalidates the
            // local bitmap, while unchanged banners do not download again.
            val revisionFile = File(cache, item.file + ".revision")
            val cachedRevision = runCatching { revisionFile.readText() }.getOrNull()
            val revisionChanged = item.revision != null && cachedRevision != item.revision
            if (allowNetwork && (bitmap == null || revisionChanged)) {
                // Versioned query defeats raw.githubusercontent.com intermediary
                // caches without allowing arbitrary URLs in the manifest.
                val url = ROOT + item.file + (item.revision?.let { "?v=" + it } ?: "")
                val bytes = readNetwork(url, MAX_IMAGE_BYTES)
                val replacement = bytes?.let(::decodeScaled)
                if (replacement != null && bytes != null) {
                    bitmap = replacement
                    runCatching {
                        local.writeBytes(bytes)
                        if (item.revision != null) revisionFile.writeText(item.revision)
                    }
                }
            }
            // Report the revision actually present on disk (which might be an
            // older image if the replacement download failed). Once a retry
            // succeeds, the signature changes and Compose updates the banner.
            val loadedRevision = runCatching { revisionFile.readText() }.getOrNull()
            bitmap?.let { NoraPromotion(item.id, item.title, item.target, it, loadedRevision) }
        }
    }

    /** Cached manifest identity plus visible image list for stable Compose state. */
    fun fingerprint(context: Context, images: List<NoraPromotion>): String {
        val manifest = runCatching {
            File(storage(context), "manifest.json").readText(Charsets.UTF_8)
        }.getOrDefault("")
        return manifest + images.joinToString(separator = "|") {
            it.id + ":" + (it.loadedRevision ?: "uncached")
        }
    }

    /** Read only from IO; 3–30 seconds to avoid excessive redraws. */
    fun rotationSeconds(context: Context): Int = runCatching {
        val manifest = File(storage(context), "manifest.json")
        if (!manifest.isFile) 8
        else JSONObject(manifest.readText(Charsets.UTF_8))
            .optInt("rotationSeconds", 8).coerceIn(3, 30)
    }.getOrDefault(8)

    /** Intended to be called from Dispatchers.IO, not from a composable draw pass. */
    fun cached(context: Context): List<NoraPromotion> = runCatching {
        val manifest = File(storage(context), "manifest.json")
        if (!manifest.isFile) emptyList() else
            render(context, manifest.readText(Charsets.UTF_8), allowNetwork = false)
    }.getOrDefault(emptyList())

    /** Poll at most every 15 seconds while Home is visible; preserve offline cache. */
    fun refresh(context: Context): List<NoraPromotion> {
        val prefs = context.getSharedPreferences("nora_ads", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check", 0L)
        val now = SystemClock.elapsedRealtime()
        if (lastCheck in 1..now && now - lastCheck < CACHE_TTL_MS) return cached(context)
        // CDN cache-busting: public GitHub raw files can remain cached even after
        // a commit. A short-lived query makes recently published manifests visible.
        val manifestUrl = ROOT + "manifest.json?refresh=" +
            (System.currentTimeMillis() / CACHE_TTL_MS)
        val bytes = readNetwork(manifestUrl, MAX_MANIFEST_BYTES)
            ?: return cached(context)
        val json = bytes.toString(Charsets.UTF_8)
        if (runCatching { descriptors(json) }.isFailure) return cached(context)
        val dir = storage(context)
        val manifest = File(dir, "manifest.json")
        // Reuse unchanged images. Only new or content-hash-changed images
        // trigger image downloads; the manifest itself is tiny.
        val refreshed = render(context, json, allowNetwork = true)
        // Remove outdated image files when campaigns are deleted or renamed.
        val activeFiles = runCatching {
            descriptors(json).flatMap { listOf(it.file, it.file + ".revision") }.toSet()
        }.getOrDefault(emptySet())
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var banners by remember { mutableStateOf<List<NoraPromotion>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var rotationSeconds by remember { mutableIntStateOf(8) }
    var lastFingerprint by remember { mutableStateOf("") }
    LaunchedEffect(context, foreground) {
        if (!foreground) return@LaunchedEffect
        // Cached content paints immediately. All file/network/decode IO stays off UI.
        if (lastFingerprint.isEmpty()) {
            val initial = withContext(Dispatchers.IO) {
                val items = NoraPromotionRepository.cached(context)
                Triple(items, NoraPromotionRepository.rotationSeconds(context),
                    NoraPromotionRepository.fingerprint(context, items))
            }
            banners = initial.first
            rotationSeconds = initial.second
            lastFingerprint = initial.third
        }
        // Re-check while this screen is visible. When the user leaves Home or
        // backgrounds the app, the effect is cancelled (no background polling).
        while (true) {
            val updated = withContext(Dispatchers.IO) {
                val items = NoraPromotionRepository.refresh(context)
                Triple(items, NoraPromotionRepository.rotationSeconds(context),
                    NoraPromotionRepository.fingerprint(context, items))
            }
            if (updated.third != lastFingerprint) {
                banners = updated.first
                rotationSeconds = updated.second
                lastFingerprint = updated.third
            }
            delay(15_000L)
        }
    }
    LaunchedEffect(banners.size, rotationSeconds) {
        page = 0
        while (banners.size > 1) {
            delay(rotationSeconds * 1000L)
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
