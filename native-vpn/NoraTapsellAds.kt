package com.v2ray.ang.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import com.v2ray.ang.BuildConfig
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional Tapsell Mediation interstitials for normal VPN connect/disconnect transitions.
 *
 * The VPN action is NEVER gated on ad inventory or ad completion. A loaded
 * interstitial is shown AFTER the requested native service state is observed.
 * Back/close controls of the ad network are owned by the SDK and never overridden.
 */
class NoraTapsellAds(private val activity: Activity?) {
    private val main = Handler(Looper.getMainLooper())
    private val zoneId = BuildConfig.NORA_TAPSELL_INTERSTITIAL_ZONE_ID
    private val configured = BuildConfig.NORA_TAPSELL_ENABLED &&
        activity != null && zoneId.isNotBlank()
    private var stopped = false
    private var loading = false
    private var readyAd: String? = null
    private var showing = false

    private val _blocking = MutableStateFlow(false)
    val blocking = _blocking.asStateFlow()

    fun start() {
        if (!configured || stopped) return
        runCatching {
            // Explicit initialization: auto-init is disabled in the manifest.
            Tapsell.initialize(requireNotNull(activity).applicationContext)
            preload()
        }
    }

    private fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else main.post { if (!stopped) action() }
    }

    fun preload() {
        if (!configured || loading || readyAd != null || showing || stopped) return
        loading = true
        try {
            Tapsell.requestInterstitialAd(zoneId, requireNotNull(activity), object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    dispatch {
                        loading = false
                        if (!stopped && adId.isNotBlank()) readyAd = adId
                    }
                }
                override fun onFailure(message: String) {
                    dispatch {
                        loading = false
                        readyAd = null
                        // No ad is normal; never freeze VPN control or user navigation.
                    }
                }
            })
        } catch (_: Exception) {
            loading = false
            readyAd = null
        }
    }

    /** Called exactly once after a user-requested native VPN state change. */
    fun onVpnOperationCompleted() {
        if (!configured || stopped || showing) return
        val adId = readyAd
        readyAd = null
        if (adId.isNullOrBlank()) {
            // Load one for the NEXT transition; do not hold user hostage to ad loading.
            preload()
            return
        }
        showing = true
        _blocking.value = true
        try {
            Tapsell.showInterstitialAd(adId, requireNotNull(activity),
                object : AdStateListener.Interstitial {
                    override fun onAdImpression() = Unit
                    override fun onAdClicked() = Unit
                    override fun onAdClosed(completionState: AdShowCompletionState) {
                        dispatch { finish() }
                    }
                    override fun onAdFailed(message: String) {
                        dispatch { finish() }
                    }
                })
        } catch (_: Exception) {
            finish()
        }
    }

    private fun finish() {
        showing = false
        _blocking.value = false
        preload()
    }

    fun dispose() {
        stopped = true
        readyAd = null
        loading = false
        showing = false
        _blocking.value = false
    }
}

fun Context.noraActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
}
