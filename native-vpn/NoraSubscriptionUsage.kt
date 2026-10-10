package com.v2ray.ang.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** Only the subscription provider can report account quota and expiry. */
internal data class NoraSubscriptionUsage(
    val upload: Long?,
    val download: Long?,
    val total: Long?,
    val expiresAtSeconds: Long?
) {
    val used: Long?
        get() {
            val up = upload ?: return null
            val down = download ?: return null
            return if (up > Long.MAX_VALUE - down) Long.MAX_VALUE else up + down
        }

    val remaining: Long?
        get() = total?.let { all -> used?.let { (all - it).coerceAtLeast(0) } }

    fun remainingTrafficLabel(): String = when {
        total == null -> "حجم نامشخص"
        total == 0L -> "حجم نامحدود"
        remaining == null -> "مصرف نامشخص"
        else -> formatBytes(remaining!!) + " باقی‌مانده"
    }

    fun remainingTimeLabel(nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val expiry = expiresAtSeconds ?: return "زمان نامشخص"
        if (expiry == 0L) return "زمان نامحدود"
        val seconds = expiry - nowSeconds
        if (seconds <= 0) return "منقضی شده"
        val days = (seconds / 86400) + if (seconds % 86400 == 0L) 0L else 1L
        return days.toString() + " روز باقی‌مانده"
    }

    fun usagePercent(): Float? =
        if (total == null || total <= 0L || used == null) null
        else (used!!.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun formatBytes(bytes: Long): String {
    val value = bytes.toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB")
    if (bytes < 1024) return bytes.toString() + " B"
    val div = listOf(1.0, 1024.0, 1024.0 * 1024, 1024.0 * 1024 * 1024, 1024.0 * 1024 * 1024 * 1024)
    val index = when {
        bytes >= 1024L * 1024 * 1024 * 1024 -> 4
        bytes >= 1024L * 1024 * 1024 -> 3
        bytes >= 1024L * 1024 -> 2
        else -> 1
    }
    val unit = units[index]
    return String.format(Locale.US, "%.1f %s", value / div[index], unit)
}

/**
 * Supports the conventional subscription-userinfo HTTP response header:
 * upload=123; download=456; total=789; expire=1712345678
 * No invented quota values and no sensitive URL logging.
 */
internal object NoraSubscriptionUsageReader {
    private val pairPattern = Regex("""(?:^|[;,\s])\s*(upload|download|total|expire)\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(header: String?): NoraSubscriptionUsage? {
        if (header.isNullOrBlank() || header.length > 4096) return null
        val fields = pairPattern.findAll(header).associate { match ->
            match.groupValues[1].lowercase(Locale.ROOT) to match.groupValues[2].toLongOrNull()
        }
        if (fields.isEmpty() || fields.values.all { it == null }) return null
        return NoraSubscriptionUsage(
            upload = fields["upload"],
            download = fields["download"],
            total = fields["total"],
            expiresAtSeconds = fields["expire"]
        )
    }

    suspend fun fetch(subscriptionUrl: String): NoraSubscriptionUsage? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(subscriptionUrl.trim()) }.getOrNull() ?: return@withContext null
        val hostname = uri.host?.lowercase(Locale.ROOT) ?: return@withContext null
        if (uri.scheme?.lowercase(Locale.ROOT) != "https" ||
            uri.userInfo != null || uri.fragment != null ||
            hostname == "localhost" || hostname.endsWith(".localhost") ||
            hostname.endsWith(".local") || hostname == "0.0.0.0" ||
            hostname.matches(Regex("[0-9.]+")) || ":" in hostname ||
            subscriptionUrl.length > 4096) return@withContext null

        // Some providers expose this header on GET but not on HEAD.
        for (method in listOf("HEAD", "GET")) {
            val connection = try {
                URL(subscriptionUrl).openConnection() as HttpsURLConnection
            } catch (_: Exception) {
                return@withContext null
            }
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "NoraProxy/0.2")
                connection.setRequestProperty("Accept", "text/plain, */*")
                val status = connection.responseCode
                if (status in 200..299) {
                    parse(connection.getHeaderField("subscription-userinfo"))?.let {
                        return@withContext it
                    }
                }
                if (status in 300..399) break // Never follow redirects with a bearer URL.
            } catch (_: Exception) {
                // Missing metadata must not interrupt VPN connectivity.
            } finally {
                connection.disconnect()
            }
        }
        null
    }
}
