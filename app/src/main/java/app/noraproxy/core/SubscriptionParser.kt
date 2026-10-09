package app.noraproxy.core

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/**
 * Parses only known share-link formats. Never invents nodes when the input is invalid.
 * Unsupported protocols are intentionally ignored rather than mislabeled.
 */
object SubscriptionParser {
    private val prefixes = listOf("vless://", "vmess://", "trojan://", "ss://")
    private const val MAX_INPUT = 2_000_000
    private const val MAX_NODES = 500

    fun parse(payload: String): List<ProxyNode> {
        require(payload.length <= MAX_INPUT) { "Subscription exceeds the 2 MB limit." }
        val decoded = decodeIfSubscriptionBase64(payload.trim())
        return decoded.lineSequence()
            .map { it.trim() }
            .filter { candidate -> prefixes.any { candidate.startsWith(it, ignoreCase = true) } }
            .take(MAX_NODES)
            .mapNotNull { line -> runCatching { parseLine(line) }.getOrNull() }
            .distinctBy { it.id }
            .toList()
    }

    private fun decodeIfSubscriptionBase64(input: String): String {
        if (input.contains("://")) return input
        val decoded = decodeBase64(input) ?: return input
        return if (prefixes.any { decoded.contains(it, ignoreCase = true) }) decoded else input
    }

    private fun decodeBase64(encoded: String): String? = runCatching {
        val normalized = encoded.trim().replace('-', '+').replace('_', '/')
            .replace("\r", "").replace("\n", "")
        val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }.getOrNull()

    private fun parseLine(line: String): ProxyNode? {
        val scheme = line.substringBefore("://").lowercase()
        val endpoint: Pair<String, Int>
        val title: String
        when (scheme) {
            "vless", "trojan" -> {
                val parsed = URI(line)
                endpoint = address(parsed.host, parsed.port) ?: return null
                title = fragment(parsed.rawFragment, scheme.uppercase())
            }
            "vmess" -> {
                val json = JSONObject(decodeBase64(line.substringAfter("://").substringBefore("#"))
                    ?: return null)
                endpoint = address(json.optString("add"), json.optString("port").toIntOrNull() ?: 0)
                    ?: return null
                title = json.optString("ps").ifBlank { "VMess" }.take(140)
            }
            "ss" -> {
                val body = line.substringAfter("://").substringBefore("#").substringBefore("?")
                val uri = if ('@' in body) {
                    val decodedServer = body.substringAfterLast('@')
                    decodedServer
                } else {
                    val legacy = decodeBase64(body) ?: return null
                    legacy.substringAfterLast('@', missingDelimiterValue = "")
                }
                endpoint = parseHostPort(uri) ?: return null
                title = fragment(line.substringAfter('#', ""), "Shadowsocks")
            }
            else -> return null
        }
        if (line.length > 12_000) return null
        return ProxyNode(
            id = stableNodeId(line),
            uri = line,
            protocol = scheme.uppercase(),
            host = endpoint.first,
            port = endpoint.second,
            title = title,
            region = RegionClassifier.fromTitle(title)
        )
    }

    private fun fragment(raw: String?, fallback: String): String =
        runCatching { URLDecoder.decode(raw.orEmpty(), "UTF-8") }.getOrDefault("")
            .trim().take(140).ifBlank { fallback }

    private fun parseHostPort(value: String): Pair<String, Int>? {
        val candidate = runCatching { URI("proxy://" + value) }.getOrNull() ?: return null
        return address(candidate.host, candidate.port)
    }

    private fun address(host: String?, port: Int): Pair<String, Int>? {
        val normalized = host?.trim()?.removePrefix("[")?.removeSuffix("]")
        if (normalized.isNullOrBlank() || normalized.length > 255 || port !in 1..65535) return null
        return normalized to port
    }
}
