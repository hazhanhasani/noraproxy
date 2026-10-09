package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.MmkvManager
import java.util.Locale

/** A route maps to a real original runtime GUID; no proxy credentials reach UI. */
internal data class NoraNode(
    val guid: String,
    val countryCode: String,
    val countryName: String,
    val flag: String,
    val latencyMs: Long
)

internal object NoraRouteSelector {
    private data class Country(val code: String, val name: String, val flag: String, val aliases: List<String>)
    private val countries = listOf(
        Country("DE","آلمان","🇩🇪",listOf("germany","آلمان","🇩🇪")),
        Country("NL","هلند","🇳🇱",listOf("netherlands","holland","هلند","🇳🇱")),
        Country("FI","فنلاند","🇫🇮",listOf("finland","فنلاند","🇫🇮")),
        Country("FR","فرانسه","🇫🇷",listOf("france","فرانسه","🇫🇷")),
        Country("GB","انگلیس","🇬🇧",listOf("united kingdom","england","انگلیس","🇬🇧")),
        Country("US","آمریکا","🇺🇸",listOf("united states","usa","america","آمریکا","🇺🇸")),
        Country("TR","ترکیه","🇹🇷",listOf("turkey","ترکیه","🇹🇷")),
        Country("JP","ژاپن","🇯🇵",listOf("japan","ژاپن","🇯🇵")),
        Country("SG","سنگاپور","🇸🇬",listOf("singapore","سنگاپور","🇸🇬")),
        Country("AE","امارات","🇦🇪",listOf("uae","dubai","امارات","🇦🇪")),
        Country("CA","کانادا","🇨🇦",listOf("canada","کانادا","🇨🇦")),
        Country("RU","روسیه","🇷🇺",listOf("russia","روسیه","🇷🇺")),
        Country("SE","سوئد","🇸🇪",listOf("sweden","سوئد","🇸🇪"))
    )

    private fun classify(text: String): Country {
        val lower = text.lowercase(Locale.ROOT)
        return countries.firstOrNull { country ->
            country.aliases.any { alias ->
                if (alias.length <= 3 && alias.all { it in 'a'..'z' }) {
                    Regex("(^|[^a-z])" + Regex.escape(alias) + "([^a-z]|$)").containsMatchIn(lower)
                } else lower.contains(alias)
            }
        } ?: Country("OTHER","سایر لوکیشن‌ها","🌐",emptyList())
    }

    fun load(): List<NoraNode> = MmkvManager.decodeAllServerList().distinct().mapNotNull { guid ->
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
        val country = classify(profile.remarks)
        NoraNode(
            guid, country.code, country.name, country.flag,
            MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
        )
    }

    // Measured native Xray delay (>0ms) beats unknown (0), which beats a failed test (<0).
    fun rank(nodes: List<NoraNode>): List<NoraNode> = nodes.sortedWith(
        compareBy<NoraNode> { if (it.latencyMs > 0L) 0 else if (it.latencyMs == 0L) 1 else 2 }
            .thenBy { if (it.latencyMs > 0L) it.latencyMs else Long.MAX_VALUE }
            .thenBy { it.guid }
    )

    fun locations(nodes: List<NoraNode>): List<NoraNode> =
        rank(nodes).distinctBy { it.countryCode }
}
