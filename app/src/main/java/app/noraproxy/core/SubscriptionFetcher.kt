package app.noraproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

object SubscriptionFetcher {
    private const val MAX_BYTES = 2_000_000

    fun validateUrl(value: String): Boolean {
        val url = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        if (!url.scheme.equals("https", true) || url.host.isNullOrBlank() ||
            url.userInfo != null || url.fragment != null || url.port !in -1..65535) return false
        val host = url.host!!.lowercase().removeSurrounding("[", "]")
        if (host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host == "0.0.0.0") return false
        // Reject numeric IPs to reduce accidental access to private local services.
        if (host.matches(Regex("[0-9.]+")) || ':' in host) return false
        return value.length <= 4096
    }

    suspend fun fetch(httpsUrl: String): String = withContext(Dispatchers.IO) {
        require(validateUrl(httpsUrl)) { "A public HTTPS subscription URL is required." }
        val connection = (URL(httpsUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "text/plain, application/octet-stream")
            setRequestProperty("User-Agent", "NoraProxy/0.1")
        }
        try {
            val status = connection.responseCode
            require(status == 200) { "Subscription server returned HTTP " + status }
            require(connection.contentLengthLong <= MAX_BYTES || connection.contentLengthLong == -1L) {
                "Subscription is too large."
            }
            connection.inputStream.use { stream ->
                val bytes = stream.readNBytes(MAX_BYTES + 1)
                require(bytes.size <= MAX_BYTES) { "Subscription exceeds the 2 MB limit." }
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }
}
