package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
import java.util.UUID

/**
 * Safe, bounded clipboard transfer. Existing locked rules are never exported,
 * overwritten, or removed by an import.
 */
internal object NoraRoutingTransfer {
    private const val MAX_CLIPBOARD_CHARS = 256_000
    private const val MAX_RULES = 100

    sealed interface ImportResult {
        data class Ready(val rules: List<RulesetItem>) : ImportResult
        data class Invalid(val message: String) : ImportResult
    }

    fun preview(text: String?): ImportResult {
        if (text.isNullOrBlank()) return ImportResult.Invalid("کلیپ‌بورد خالی است")
        if (text.length > MAX_CLIPBOARD_CHARS) {
            return ImportResult.Invalid("حجم اطلاعات قوانین بیش از حد مجاز است")
        }
        return runCatching {
            val parsed = JsonUtil.fromJsonSafe(text, Array<RulesetItem>::class.java)
                ?: return@runCatching ImportResult.Invalid("فرمت JSON قوانین معتبر نیست")
            if (parsed.size !in 1..MAX_RULES) {
                return@runCatching ImportResult.Invalid("بین ۱ تا ۱۰۰ قانون وارد کنید")
            }
            val imported = ArrayList<RulesetItem>(parsed.size)
            for (item in parsed) {
                if (item.locked == true) {
                    return@runCatching ImportResult.Invalid(
                        "قانون قفل‌شده قابل واردکردن نیست؛ ابتدا نسخه بدون قفل تهیه کنید"
                    )
                }
                val issue = NoraAdvancedValidation.validateRule(
                    item.remarks.orEmpty(),
                    item.domain?.joinToString(",").orEmpty(),
                    item.ip?.joinToString(",").orEmpty(),
                    item.process?.joinToString(",").orEmpty(),
                    item.protocol?.joinToString(",").orEmpty(),
                    item.port.orEmpty(),
                    item.network.orEmpty(),
                    item.outboundTag
                )
                if (issue != null) return@runCatching ImportResult.Invalid("قانون نامعتبر: $issue")
                // Generate fresh IDs so imported rules cannot collide with protected rules.
                imported += item.copy(
                    id = UUID.randomUUID().toString(),
                    locked = false
                )
            }
            ImportResult.Ready(imported)
        }.getOrElse {
            ImportResult.Invalid("خواندن قوانین امکان‌پذیر نیست؛ JSON را بررسی کنید")
        }
    }

    fun replaceUnlocked(
        current: List<RulesetItem>,
        incoming: List<RulesetItem>
    ): List<RulesetItem> {
        require(incoming.isNotEmpty() && incoming.size <= MAX_RULES)
        require(incoming.none { it.locked == true })
        return current.filter { it.locked == true }.map { it.copy() } +
            incoming.map { it.copy(locked = false) }
    }

    fun exportUnlocked(current: List<RulesetItem>): String? {
        val unlocked = current.filter { it.locked != true }
        if (unlocked.isEmpty()) return null
        return JsonUtil.toJson(unlocked)
    }
}
