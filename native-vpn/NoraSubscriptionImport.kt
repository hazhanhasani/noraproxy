package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SubscriptionUpdater
import java.net.URI
import java.util.Locale

internal sealed interface NoraImportPayload {
    data class Subscription(val url: String) : NoraImportPayload
    data class Raw(val text: String) : NoraImportPayload
    data class Invalid(val reason: String) : NoraImportPayload
}

/**
 * One import entry for pasted links, gallery QR, camera QR and shared text.
 * A remote subscription is bound to the selected group, never auto-creates "import sub".
 */
internal object NoraImportRouter {
    fun classify(value: String): NoraImportPayload {
        val text = value.trim()
        if (text.isEmpty()) return NoraImportPayload.Invalid("ابتدا لینک یا کانفیگ را وارد کنید")
        if (text.length > 65_536) return NoraImportPayload.Invalid("متن بیش از حد طولانی است")

        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val webLinks = lines.filter {
            it.startsWith("https://", true) || it.startsWith("http://", true)
        }

        if (webLinks.isNotEmpty()) {
            if (lines.size != 1 || webLinks.size != 1) {
                return NoraImportPayload.Invalid("در هر گروه تنها یک لینک اشتراک وارد کنید؛ کانفیگ‌های خام را جدا وارد کنید")
            }
            if (!text.startsWith("https://", true)) {
                return NoraImportPayload.Invalid("لینک اشتراک باید با HTTPS شروع شود")
            }
            val invalidReason = validateUrl(text)
            return if (invalidReason != null) NoraImportPayload.Invalid(invalidReason)
            else NoraImportPayload.Subscription(text)
        }

        val protocols = listOf("vless://", "vmess://", "trojan://", "ss://",
            "socks://", "hysteria2://", "hy2://", "tuic://", "wireguard://")
        if (lines.any { line -> protocols.none { line.startsWith(it, true) } }) {
            return NoraImportPayload.Invalid("فقط لینک کانفیگ معتبر یا لینک اشتراک HTTPS وارد کنید")
        }
        return NoraImportPayload.Raw(text)
    }

    internal fun validateUrl(url: String): String? {
        if (url.length > 4096) return "لینک اشتراک بیش از حد طولانی است"
        val parsed = runCatching { URI(url) }.getOrNull()
            ?: return "آدرس اشتراک معتبر نیست"
        val host = parsed.host?.lowercase(Locale.ROOT)
            ?: return "دامنه اشتراک معتبر نیست"
        if (parsed.scheme?.lowercase(Locale.ROOT) != "https" || parsed.userInfo != null ||
            parsed.fragment != null || parsed.port !in -1..65535 || host == "localhost" ||
            host.endsWith(".localhost") || host.endsWith(".local") || host == "0.0.0.0" ||
            host.matches(Regex("[0-9.]+")) || ':' in host) {
            return "فقط لینک اشتراک HTTPS عمومی معتبر پذیرفته می‌شود"
        }
        return null
    }

    /**
     * The selected group must already exist. Saves the URL without creating another
     * subscription ID; the caller then requests an actual upstream group refresh.
     */
    fun attachSubscription(groupId: String, url: String): Result<Boolean> = runCatching {
        require(groupId.isNotBlank()) { "ابتدا یک گروه انتخاب کنید" }
        val current = MmkvManager.decodeSubscription(groupId)
            ?: error("گروه انتخاب‌شده وجود ندارد")
        val invalid = validateUrl(url)
        require(invalid == null) { invalid ?: "Invalid URL" }
        require(MmkvManager.decodeSubscriptions().none {
            it.guid != groupId && it.subscription.url == url
        }) { "این لینک قبلاً در گروه دیگری ثبت شده است" }

        val changed = current.url != url
        if (changed) {
            // Do not touch the group's GUID or remove existing profiles on a failed fetch.
            current.url = url
            current.enabled = true
            current.autoUpdate = true
            MmkvManager.encodeSubscription(groupId, current)
            SubscriptionUpdater.syncOne(subId = groupId)
            SettingsChangeManager.makeSetupGroupTab()
        }
        changed
    }
}
