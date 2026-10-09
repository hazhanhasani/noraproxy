package app.noraproxy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.noraproxy.core.EndpointProbe
import app.noraproxy.core.RankedNode
import app.noraproxy.core.RouteSelector
import app.noraproxy.core.SecureSettings
import app.noraproxy.core.SubscriptionFetcher
import app.noraproxy.core.SubscriptionParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoraUiState(
    val url: String = "",
    val seller: String = "",
    val busy: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val ranked: List<RankedNode> = emptyList(),
    val selectedId: String? = null,
    val notice: String = "لینک اشتراک خود را وارد کنید.",
    val error: Boolean = false
) {
    val reachable: Int get() = ranked.count { it.stats.reachable }
    val regions: List<RankedNode> get() = RouteSelector.bestPerRegion(ranked)
    val selected: RankedNode? get() = ranked.firstOrNull { it.node.id == selectedId }
}

class NoraViewModel(application: Application) : AndroidViewModel(application) {
    private val secureSettings = SecureSettings(application)
    private val mutable = MutableStateFlow(NoraUiState())
    val state: StateFlow<NoraUiState> = mutable.asStateFlow()

    init {
        val loaded = runCatching { secureSettings.load() }.getOrDefault("" to "")
        mutable.update { it.copy(url = loaded.first, seller = loaded.second) }
    }

    fun setSubscription(value: String) {
        mutable.update { it.copy(url = value.take(4096)) }
    }

    fun setSeller(value: String) {
        mutable.update { it.copy(seller = value.take(80)) }
    }

    /** App deep links pre-fill a form, but never silently fetch secret URLs. */
    fun prefill(url: String?, seller: String?) {
        if (url != null) setSubscription(url)
        if (seller != null) setSeller(seller)
        mutable.update { it.copy(notice = "اطلاعات فروشنده وارد شد؛ برای بررسی دکمه شروع را بزنید.") }
    }

    fun choose(nodeId: String) {
        mutable.update { state ->
            if (state.ranked.any { it.node.id == nodeId && it.stats.reachable }) {
                state.copy(selectedId = nodeId)
            } else state
        }
    }

    fun optimize() {
        if (mutable.value.busy) return
        val url = mutable.value.url.trim()
        val seller = mutable.value.seller.trim()
        if (!SubscriptionFetcher.validateUrl(url)) {
            mutable.update {
                it.copy(error = true, notice = "آدرس اشتراک باید HTTPS عمومی و معتبر باشد.")
            }
            return
        }
        viewModelScope.launch {
            mutable.update {
                it.copy(busy = true, error = false, progress = 0, total = 0,
                    notice = "در حال دریافت و تحلیل اشتراک...", ranked = emptyList(), selectedId = null)
            }
            try {
                // Persist even when the remote endpoint temporarily fails.
                secureSettings.save(url, seller)
                val content = SubscriptionFetcher.fetch(url)
                val parsed = SubscriptionParser.parse(content).take(250)
                require(parsed.isNotEmpty()) { "هیچ کانفیگ پشتیبانی‌شده‌ای پیدا نشد." }
                mutable.update {
                    it.copy(total = parsed.size, notice = "در حال بررسی دسترسی سرورها از اینترنت شما...")
                }
                val probes = EndpointProbe.test(parsed) { completed, total ->
                    mutable.update { it.copy(progress = completed, total = total) }
                }
                val ranked = RouteSelector.rank(parsed, probes)
                val first = ranked.firstOrNull { it.stats.reachable }
                mutable.update {
                    it.copy(
                        ranked = ranked,
                        selectedId = first?.node?.id,
                        error = first == null,
                        notice = if (first != null) {
                            "بررسی TCP انجام شد. اتصال واقعی VPN باید در کلاینت مقصد آزمایش شود."
                        } else "هیچ سروری در آزمون TCP پاسخ نداد؛ اشتراک یا شبکه را بررسی کنید."
                    )
                }
            } catch (e: Exception) {
                mutable.update {
                    it.copy(error = true, notice = e.message?.take(160) ?: "دریافت یا بررسی اشتراک ناموفق بود.")
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
