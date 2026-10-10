package com.v2ray.ang.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.v2ray.ang.BuildConfig
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preloads interstitials independently of the native VPN lifecycle.
 *
 * Inventory, location/consent, DNS, network access and ad SDK rules can prevent
 * impressions on some devices; requests cannot guarantee a rendered ad.
 * VPN connect and disconnect must ALWAYS work regardless of advertising.
 */
class NoraTapsellAds(private val activity: Activity?) {
    private val main = Handler(Looper.getMainLooper())
    private val zoneId = BuildConfig.NORA_TAPSELL_INTERSTITIAL_ZONE_ID
    private val configured = BuildConfig.NORA_TAPSELL_ENABLED &&
        activity != null && zoneId.isNotBlank()

    private var stopped = false
    private var initialized = false
    private var loading = false
    private var showing = false
    private var readyAd: String? = null
    private var readyAt = 0L
    private var consecutiveFailures = 0
    private var attempts = 0
    private var loads = 0
    private var impressions = 0
    private var requestGeneration = 0
    private var adGeneration = 0

    private val _blocking = MutableStateFlow(false)
    val blocking = _blocking.asStateFlow()
    private val _diagnostics = MutableStateFlow(
        if (configured) "آماده‌سازی تبلیغات…" else "تبلیغات در این نسخه فعال نیست"
    )
    val diagnostics = _diagnostics.asStateFlow()

    private var retry: Runnable? = null
    private var timeout: Runnable? = null
    private var showTimeout: Runnable? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post { if (!stopped) block() }
    }

    fun start() = onMain {
        if (stopped || !configured) return@onMain
        if (!initialized) {
            try {
                // AUTO_INIT=false in the manifest. Initializing again for each
                // recomposition may interrupt pending ad requests on some devices.
                Tapsell.initialize(requireNotNull(activity).applicationContext)
                initialized = true
            } catch (_: Exception) {
                _diagnostics.value = "مقداردهی SDK ناموفق بود؛ تلاش مجدد"
                scheduleRetry()
                return@onMain
            }
        }
        preload()
    }

    fun preload() = onMain {
        if (!configured || stopped || showing || loading) return@onMain
        if (!initialized) { start(); return@onMain }
        if (readyAd != null && SystemClock.elapsedRealtime() - readyAt < MAX_AD_AGE_MS) return@onMain
        readyAd = null
        retry?.let { main.removeCallbacks(it) }
        retry = null
        loading = true
        attempts++
        _diagnostics.value = "در حال درخواست تبلیغ… (تلاش ${persianCount(attempts)})"
        val generation = ++requestGeneration
        timeout = Runnable {
            if (!stopped && loading && generation == requestGeneration) {
                requestGeneration++
                loading = false
                _diagnostics.value = "پاسخ تبلیغ دیر رسید؛ درخواست مجدد"
                scheduleRetry()
            }
        }.also { main.postDelayed(it, REQUEST_TIMEOUT_MS) }

        try {
            Tapsell.requestInterstitialAd(
                zoneId,
                requireNotNull(activity),
                object : RequestResultListener {
                    override fun onSuccess(adId: String) = onMain {
                        if (stopped || generation != requestGeneration) return@onMain
                        clearRequestTimeout()
                        loading = false
                        if (adId.isBlank()) {
                            _diagnostics.value = "تبلیغ قابل نمایش دریافت نشد"
                            scheduleRetry()
                            return@onMain
                        }
                        consecutiveFailures = 0
                        readyAd = adId
                        readyAt = SystemClock.elapsedRealtime()
                        loads++
                        _diagnostics.value = "تبلیغ آماده نمایش است"
                    }

                    override fun onFailure(message: String) = onMain {
                        if (stopped || generation != requestGeneration) return@onMain
                        clearRequestTimeout()
                        loading = false
                        readyAd = null
                        // Do not log SDK messages or ad IDs: they may include identifiers.
                        _diagnostics.value = if (
                            message.contains("fill", ignoreCase = true) ||
                            message.contains("available", ignoreCase = true)
                        ) "در حال حاضر تبلیغی برای این دستگاه موجود نیست"
                        else "درخواست تبلیغ ناموفق بود؛ تلاش مجدد خودکار"
                        scheduleRetry()
                    }
                }
            )
        } catch (_: Exception) {
            if (generation == requestGeneration) {
                clearRequestTimeout()
                loading = false
                readyAd = null
                _diagnostics.value = "خطای ارتباط با سرویس تبلیغات؛ تلاش مجدد"
                scheduleRetry()
            }
        }
    }

    /**
     * Called only after the user-requested actual VPN state change. Never
     * postpones a necessary disconnect or Android system navigation.
     */
    fun onVpnOperationCompleted() = onMain {
        if (!configured || stopped || showing) return@onMain
        val adId = readyAd
        readyAd = null
        if (adId.isNullOrBlank() ||
            SystemClock.elapsedRealtime() - readyAt > MAX_AD_AGE_MS) {
            preload() // Missing inventory: don't freeze the screen.
            return@onMain
        }
        showing = true
        _blocking.value = true
        _diagnostics.value = "در حال نمایش تبلیغ"
        val generation = ++adGeneration
        showTimeout = Runnable {
            if (!stopped && showing && generation == adGeneration) {
                _diagnostics.value = "زمان انتظار نمایش تمام شد"
                finishAd()
            }
        }.also { main.postDelayed(it, SHOW_WATCHDOG_MS) }
        try {
            Tapsell.showInterstitialAd(
                adId,
                requireNotNull(activity),
                object : AdStateListener.Interstitial {
                    override fun onAdImpression() = onMain {
                        if (!stopped && generation == adGeneration) {
                            impressions++
                            _diagnostics.value = "تبلیغ نمایش داده شد"
                        }
                    }
                    override fun onAdClicked() = Unit
                    override fun onAdClosed(completionState: AdShowCompletionState) = onMain {
                        if (generation == adGeneration) finishAd()
                    }
                    override fun onAdFailed(message: String) = onMain {
                        if (generation == adGeneration) {
                            _diagnostics.value = "تبلیغ بارگیری‌شده روی این دستگاه نمایش داده نشد"
                            finishAd()
                        }
                    }
                }
            )
        } catch (_: Exception) {
            finishAd()
        }
    }

    private fun finishAd() {
        if (!showing) return
        showTimeout?.let { main.removeCallbacks(it) }
        showTimeout = null
        showing = false
        _blocking.value = false
        ++adGeneration
        preload()
    }

    private fun scheduleRetry() {
        if (stopped || !configured) return
        retry?.let { main.removeCallbacks(it) }
        val delay = (RETRY_BASE_MS * (1L shl consecutiveFailures.coerceAtMost(3)))
            .coerceAtMost(RETRY_MAX_MS)
        consecutiveFailures++
        retry = Runnable {
            retry = null
            if (!stopped) start()
        }.also { main.postDelayed(it, delay) }
    }

    private fun clearRequestTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    private fun persianCount(value: Int): String = value.toString()
        .map { ('۰'.code + it.digitToInt()).toChar() }.joinToString("")

    fun dispose() {
        stopped = true
        ++requestGeneration
        ++adGeneration
        clearRequestTimeout()
        retry?.let { main.removeCallbacks(it) }
        showTimeout?.let { main.removeCallbacks(it) }
        retry = null
        showTimeout = null
        readyAd = null
        loading = false
        showing = false
        _blocking.value = false
    }

    companion object {
        private const val RETRY_BASE_MS = 15_000L
        private const val RETRY_MAX_MS = 120_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val SHOW_WATCHDOG_MS = 120_000L
        private const val MAX_AD_AGE_MS = 8 * 60 * 1000L
    }
}

fun Context.noraActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
}
