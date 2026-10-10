package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import java.net.URI
import java.util.Locale

internal sealed interface NoraImportPayload {
    data class Subscriptions(val urls: List<String>) : NoraImportPayload
    data class Raw(val text: String) : NoraImportPayload
    data class Invalid(val reason: String) : NoraImportPayload
}

internal data class NoraResolvedGroup(val guid: String, val created: Boolean)

/**
 * No user-selected group is needed:
 * - Each unique HTTPS subscription owns a distinct group.
 * - Raw share links always go to the persistent Default (local-only) group.
 * - An existing exact URL is reused rather than duplicating the group.
 */
internal object NoraImportRouter {
    private val protocols = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "socks://",
        "hysteria2://", "hy2://", "tuic://", "wireguard://"
    )

    fun classify(value: String): NoraImportPayload {
        val text = value.trim()
        if (text.isEmpty()) return NoraImportPayload.Invalid("ابتدا لینک یا کانفیگ را وارد کنید")
        if (text.length > 65_536) return NoraImportPayload.Invalid("متن بیش از حد طولانی است")

        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .distinct().toList()
        val links = lines.filter {
            it.startsWith("https://", true) || it.startsWith("http://", true)
        }
        if (links.isNotEmpty()) {
            if (links.size != lines.size) {
                return NoraImportPayload.Invalid("لینک‌های اشتراک و کانفیگ خام را جداگانه وارد کنید")
            }
            if (links.size > 20) return NoraImportPayload.Invalid("حداکثر ۲۰ اشتراک در هر بار")
            for (link in links) {
                val error = validateUrl(link)
                if (error != null) return NoraImportPayload.Invalid(error)
            }
            return NoraImportPayload.Subscriptions(links)
        }
        if (lines.any { line -> protocols.none { line.startsWith(it, true) } }) {
            return NoraImportPayload.Invalid("فقط کانفیگ خام یا لینک اشتراک معتبر وارد کنید")
        }
        return NoraImportPayload.Raw(lines.joinToString("\n"))
    }

    fun validateUrl(url: String): String? {
        if (url.length > 4096) return "لینک اشتراک بیش از حد طولانی است"
        val parsed = runCatching { URI(url) }.getOrNull()
            ?: return "آدرس اشتراک معتبر نیست"
        val host = parsed.host?.lowercase(Locale.ROOT)
            ?: return "دامنه اشتراک معتبر نیست"
        if (parsed.scheme?.lowercase(Locale.ROOT) != "https" ||
            parsed.userInfo != null || parsed.fragment != null ||
            parsed.port == 0 || parsed.port > 65535 ||
            host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host == "0.0.0.0" ||
            host.matches(Regex("[0-9.]+")) || ':' in host) {
            return "لینک اشتراک باید آدرس HTTPS عمومی معتبر باشد"
        }
        return null
    }

    /**
     * Uses the upstream constant so raw server routing continues to work
     * even if a different subscription is selected or the app is connected.
     */
    fun ensureRawDefaultGroup(): String = synchronized(this) {
        val defaultId = AppConfig.DEFAULT_SUBSCRIPTION_ID
        val default = MmkvManager.decodeSubscription(defaultId)
        if (default == null) {
            MmkvManager.encodeSubscription(defaultId, SubscriptionItem(remarks = "Default"))
            SettingsChangeManager.makeSetupGroupTab()
            return@synchronized defaultId
        }
        if (default.url.isBlank()) return@synchronized defaultId

        // Preserve an older remote subscription stored in the upstream default ID.
        // Never silently overwrite its URL or profile list.
        val fallback = MmkvManager.decodeSubscriptions().firstOrNull {
            it.subscription.url.isBlank() && it.subscription.remarks == "Default · خام"
        }
        if (fallback != null) return@synchronized fallback.guid
        val localId = Utils.getUuid()
        MmkvManager.encodeSubscription(localId, SubscriptionItem(remarks = "Default · خام"))
        SettingsChangeManager.makeSetupGroupTab()
        localId
    }

    fun createOrFindSubscription(url: String): NoraResolvedGroup = synchronized(this) {
        require(validateUrl(url) == null) { "لینک اشتراک معتبر نیست" }
        val existing = MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.url == url }
        if (existing != null) return@synchronized NoraResolvedGroup(existing.guid, false)

        val host = requireNotNull(URI(url).host).lowercase(Locale.ROOT)
        val name = ("اشتراک " + host).take(64)
        val groupId = Utils.getUuid()
        val item = SubscriptionItem(
            remarks = name,
            url = url,
            enabled = true,
            autoUpdate = true
        )
        MmkvManager.encodeSubscription(groupId, item)
        SubscriptionUpdater.syncOne(subId = groupId)
        SettingsChangeManager.makeSetupGroupTab()
        NoraResolvedGroup(groupId, true)
    }
}
