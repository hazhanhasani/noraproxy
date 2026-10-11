package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.MmkvManager
import java.util.Locale

/** An ISO 3166-1 location, with a few commonly used non-ISO location aliases. */
internal data class NoraCountry(val code: String, val name: String, val flag: String)

internal data class NoraNode(
    val guid: String,
    val countryCode: String,
    val countryName: String,
    val flag: String,
    val latencyMs: Long
)

internal object NoraRouteSelector {
    private val persian = Locale.forLanguageTag("fa")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670]")
    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')
        .replace(diacritics, "").replace(punctuation, " ").trim()

    private fun flag(code: String): String =
        String(Character.toChars(0x1F1E6 + (code[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (code[1] - 'A')))

    // Android/Java ships the full ISO inventory, including dependent territories.
    private val regions: Map<String, Locale> = Locale.getISOCountries()
        .associateWith { code -> Locale.Builder().setRegion(code).build() }
    private val countries: Map<String, NoraCountry> = regions.mapValues { (code, locale) ->
        val localized = locale.getDisplayCountry(persian)
        val english = locale.getDisplayCountry(Locale.ENGLISH)
        NoraCountry(code, localized.takeUnless { it.isBlank() || it.equals(code, true) } ?: english, flag(code))
    } + mapOf("XK" to NoraCountry("XK", "کوزوو", "🇽🇰"))

    internal val supportedCountryCount: Int get() = countries.size

    /** Common tags seen in subscription names, plus Persian and historic variants. */
    private val extraAliases: Map<String, List<String>> = mapOf(
        "US" to listOf("usa", "u s a", "america", "آمریکا", "ایالات متحده", "new york", "los angeles"),
        "GB" to listOf("uk", "britain", "great britain", "england", "scotland", "wales", "انگلیس", "انگلستان", "بریتانیا", "london"),
        "DE" to listOf("germany", "deutschland", "آلمان", "frankfurt", "berlin", "munich"),
        "NL" to listOf("holland", "netherlands", "هلند", "amsterdam", "rotterdam"),
        "AE" to listOf("uae", "u a e", "emirates", "امارات", "دبی", "dubai", "abu dhabi"),
        "TR" to listOf("turkey", "turkiye", "türkiye", "ترکیه", "استانبول", "istanbul", "ankara"),
        "RU" to listOf("russia", "روسیه", "moscow", "مسکو"),
        "KR" to listOf("south korea", "republic of korea", "کره جنوبی", "seoul"),
        "KP" to listOf("north korea", "کره شمالی"),
        "CN" to listOf("china", "چین", "beijing", "shanghai"),
        "TW" to listOf("taiwan", "تایوان", "taipei"),
        "HK" to listOf("hong kong", "هنگ کنگ"),
        "IR" to listOf("iran", "ایران", "tehran", "تهران"),
        "FR" to listOf("france", "فرانسه", "paris", "پاریس"),
        "FI" to listOf("finland", "فنلاند", "helsinki"),
        "SE" to listOf("sweden", "سوئد", "stockholm"),
        "NO" to listOf("norway", "نروژ", "oslo"),
        "DK" to listOf("denmark", "دانمارک", "copenhagen"),
        "CH" to listOf("switzerland", "swiss", "سوئیس", "زوریخ", "zurich"),
        "AT" to listOf("austria", "اتریش", "vienna"),
        "ES" to listOf("spain", "اسپانیا", "madrid"),
        "IT" to listOf("italy", "ایتالیا", "rome", "milan"),
        "JP" to listOf("japan", "ژاپن", "tokyo", "توکیو"),
        "SG" to listOf("singapore", "سنگاپور"),
        "IN" to listOf("india", "هند", "mumbai"),
        "CA" to listOf("canada", "کانادا", "toronto", "تورنتو", "montreal"),
        "AU" to listOf("australia", "استرالیا", "sydney", "melbourne"),
        "NZ" to listOf("new zealand", "نیوزیلند", "auckland"),
        "BR" to listOf("brazil", "brasil", "برزیل", "sao paulo"),
        "AR" to listOf("argentina", "آرژانتین"),
        "UA" to listOf("ukraine", "اوکراین", "kyiv", "kiev"),
        "PL" to listOf("poland", "لهستان", "warsaw"),
        "CZ" to listOf("czechia", "czech republic", "جمهوری چک", "چک", "prague"),
        "RO" to listOf("romania", "رومانی"),
        "HU" to listOf("hungary", "مجارستان"),
        "SA" to listOf("saudi arabia", "عربستان", "riyadh", "ksa"),
        "QA" to listOf("qatar", "قطر", "doha"),
        "BH" to listOf("bahrain", "بحرین"),
        "IQ" to listOf("iraq", "عراق", "baghdad"),
        "EG" to listOf("egypt", "مصر", "cairo"),
        "ZA" to listOf("south africa", "آفریقای جنوبی"),
        "XK" to listOf("kosovo", "کوزوو"),
        "CI" to listOf("ivory coast", "cote d ivoire", "ساحل عاج"),
        "CD" to listOf("dr congo", "drc", "democratic republic of the congo")
    )

    private data class Matcher(val key: String, val country: NoraCountry, val regex: Regex)
    private val patterns: List<Matcher> = countries.values.flatMap { country ->
        val locale = regions[country.code]
        val names = listOfNotNull(
            country.code,
            locale?.let { runCatching { it.getISO3Country() }.getOrNull() },
            country.name,
            locale?.getDisplayCountry(Locale.ENGLISH),
            locale?.getDisplayCountry(locale)
        ) + extraAliases[country.code].orEmpty()
        names.mapNotNull { raw ->
            val key = normalize(raw)
            if (key.isBlank()) null else Matcher(
                key, country,
                Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(key) + "(?![\\p{L}\\p{N}])")
            )
        }
    }.distinctBy { it.country.code to it.key }.sortedByDescending { it.key.length }

    /**
     * Flags are most reliable. In ambiguous cases we do not guess country/IP:
     * an unrecognized location is preserved as its own entry, not "Other".
     */
    internal fun identify(label: String, unknownKey: String = ""): NoraCountry {
        var index = 0
        while (index < label.length) {
            val first = label.codePointAt(index)
            val next = index + Character.charCount(first)
            if (first in 0x1F1E6..0x1F1FF && next < label.length) {
                val second = label.codePointAt(next)
                if (second in 0x1F1E6..0x1F1FF) {
                    val code = "" + ('A' + (first - 0x1F1E6)) + ('A' + (second - 0x1F1E6))
                    countries[code]?.let { return it }
                }
            }
            index = next
        }
        val normalized = normalize(label)
        patterns.firstOrNull { it.regex.containsMatchIn(normalized) }?.let { return it.country }
        return NoraCountry("UNKNOWN:" + unknownKey, "کشور نامشخص", "🌐")
    }

    fun load(groupId: String): List<NoraNode> = MmkvManager.decodeServerList(groupId)
        .distinct().mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val country = identify(profile.remarks, guid)
            NoraNode(
                guid, country.code, country.name, country.flag,
                MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            )
        }

    // Measured native delay (>0ms) beats untested (0), which beats failed (<0).
    fun rank(nodes: List<NoraNode>): List<NoraNode> = nodes.sortedWith(
        compareBy<NoraNode> { if (it.latencyMs > 0L) 0 else if (it.latencyMs == 0L) 1 else 2 }
            .thenBy { if (it.latencyMs > 0L) it.latencyMs else Long.MAX_VALUE }
            .thenBy { it.guid }
    )

    fun locations(nodes: List<NoraNode>): List<NoraNode> =
        rank(nodes).distinctBy { it.countryCode }
}
