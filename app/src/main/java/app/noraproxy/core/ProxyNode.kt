package app.noraproxy.core

import java.security.MessageDigest
import java.util.Locale

data class ProxyNode(
    val id: String,
    val uri: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val title: String,
    val region: Region
)

data class Region(val key: String, val title: String, val flag: String)

object RegionClassifier {
    private data class Match(val key: String, val title: String, val flag: String, val keys: List<String>)
    private val known = listOf(
        Match("de", "آلمان", "🇩🇪", listOf("germany", "deutschland", "آلمان", "🇩🇪")),
        Match("nl", "هلند", "🇳🇱", listOf("netherlands", "holland", "هلند", "🇳🇱")),
        Match("fi", "فنلاند", "🇫🇮", listOf("finland", "فنلاند", "🇫🇮")),
        Match("fr", "فرانسه", "🇫🇷", listOf("france", "فرانسه", "🇫🇷")),
        Match("gb", "انگلیس", "🇬🇧", listOf("united kingdom", "england", "britain", "انگلیس", "بریتانیا", "🇬🇧")),
        Match("us", "آمریکا", "🇺🇸", listOf("united states", "america", "usa", "آمریکا", "🇺🇸")),
        Match("tr", "ترکیه", "🇹🇷", listOf("turkey", "türkiye", "ترکیه", "🇹🇷")),
        Match("jp", "ژاپن", "🇯🇵", listOf("japan", "ژاپن", "🇯🇵")),
        Match("sg", "سنگاپور", "🇸🇬", listOf("singapore", "سنگاپور", "🇸🇬")),
        Match("ae", "امارات", "🇦🇪", listOf("dubai", "uae", "emirates", "امارات", "دبی", "🇦🇪")),
        Match("ca", "کانادا", "🇨🇦", listOf("canada", "کانادا", "🇨🇦")),
        Match("ru", "روسیه", "🇷🇺", listOf("russia", "روسیه", "🇷🇺"))
    )

    fun fromTitle(title: String): Region {
        val normalized = title.lowercase(Locale.ROOT)
        for (candidate in known) {
            if (candidate.keys.any { normalized.contains(it) }) {
                return Region(candidate.key, candidate.title, candidate.flag)
            }
        }
        return Region("other", "سایر سرورها", "🌐")
    }
}

fun stableNodeId(uri: String): String =
    MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(20)
