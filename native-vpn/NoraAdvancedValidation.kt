package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.Utils
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Pure input validation for Nora's advanced editors. No network calls or profile mutation.
 * Existing rules keep their original ID, enabled state, and lock flag.
 */
internal object NoraAdvancedValidation {
    fun csv(text: String, maxItems: Int = 64): List<String>? {
        if (text.length > 4096) return null
        if (text.isBlank()) return emptyList()
        val values = text.replace('\n', ',').split(',')
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (values.size > maxItems || values.any { it.length > 256 ||
            it.any { char -> char.code < 32 || char.code == 127 }
        }) return null
        return values
    }

    fun validatePorts(value: String): Boolean {
        if (value.isBlank()) return true
        if (value.length > 256) return false
        return value.split(',').all { range ->
            val parts = range.trim().split('-')
            if (parts.size !in 1..2) return@all false
            val nums = parts.map { it.trim().toIntOrNull() }
            nums.all { it != null && it in 1..65535 } &&
                (nums.size == 1 || nums[0]!! <= nums[1]!!)
        }
    }

    fun validateRule(
        remarks: String,
        domains: String,
        ips: String,
        processes: String,
        protocols: String,
        ports: String,
        network: String,
        outbound: String
    ): String? {
        if (remarks.isBlank() || remarks.length > 64) return "نام قانون باید ۱ تا ۶۴ نویسه باشد"
        val fields = listOf(domains, ips, processes, protocols)
        if (fields.any { csv(it) == null }) return "فهرست شروط بیش از حد طولانی یا نامعتبر است"
        if (!validatePorts(ports)) return "پورت باید در بازه ۱ تا ۶۵۵۳۵ باشد"
        if (network !in listOf("", "tcp", "udp", "tcp,udp")) return "نوع شبکه معتبر نیست"
        if (outbound.isBlank() || outbound.length > 128 ||
            outbound.any { it.code < 32 || it.code == 127 })
            return "مسیر خروجی معتبر نیست"
        if (fields.all { csv(it).isNullOrEmpty() } && ports.isBlank() &&
            network.isBlank()) return "برای جلوگیری از قانون سراسری ناخواسته، حداقل یک شرط مشخص کنید"
        return null
    }

    fun buildRule(
        existing: RulesetItem?,
        remarks: String,
        domains: String,
        ips: String,
        processes: String,
        protocols: String,
        ports: String,
        network: String,
        outbound: String
    ): RulesetItem {
        check(validateRule(remarks, domains, ips, processes, protocols,
            ports, network, outbound) == null) { "Invalid routing rule" }
        return (existing?.copy() ?: RulesetItem()).apply {
            id = existing?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            this.remarks = remarks.trim()
            domain = csv(domains)?.takeIf { it.isNotEmpty() }
            ip = csv(ips)?.takeIf { it.isNotEmpty() }
            process = csv(processes)?.takeIf { it.isNotEmpty() }
            protocol = csv(protocols)?.takeIf { it.isNotEmpty() }
            port = ports.trim().takeIf { it.isNotBlank() }
            this.network = network.takeIf { it.isNotBlank() }
            outboundTag = outbound.trim()
        }
    }

    /**
     * Upstream Xray SettingsManager splits resolver values on commas without
     * trimming. Store a normalized value to avoid silently dropping a server.
     */
    fun normalizedDnsCsv(text: String): String {
        val entries = requireNotNull(csv(text, maxItems = 8)) { "Invalid DNS list" }
        return entries.joinToString(",") { item ->
            val separator = item.indexOf("://")
            if (separator > 0) {
                item.substring(0, separator).lowercase(Locale.ROOT) + item.substring(separator)
            } else item
        }
    }

    /** Returns human-readable error, or null when safe to save. */
    fun validateDnsCsv(text: String, vpnOnly: Boolean): String? {
        val values = csv(text, maxItems = 8)
            ?: return "حداکثر ۸ سرور DNS معتبر با طول محدود وارد کنید"
        return values.firstNotNullOfOrNull { value ->
            if (Utils.isPureIpAddress(value)) return@firstNotNullOfOrNull null
            if (vpnOnly) return@firstNotNullOfOrNull "DNS تونل باید فقط IP باشد"
            val uri = runCatching { URI(value) }.getOrNull()
            val scheme = uri?.scheme?.lowercase(Locale.ROOT)
            if (uri == null || scheme !in setOf("https", "tcp", "quic") ||
                uri.host.isNullOrBlank() || uri.userInfo != null ||
                uri.fragment != null || (uri.port != -1 && uri.port !in 1..65535) ||
                uri.host!!.any { it.code < 32 || it.code == 127 }) {
                "سرور DNS نامعتبر است؛ از IP یا HTTPS/TCP/QUIC معتبر استفاده کنید"
            } else null
        }
    }
}
