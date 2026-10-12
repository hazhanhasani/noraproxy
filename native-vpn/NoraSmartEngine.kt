package com.v2ray.ang.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * Foreground-only adaptive routing. Native Xray/MMKV measurements are the only
 * inputs. No probing of arbitrary URLs, proxy credential reads or IP geolocation.
 */
internal class NoraSmartEngine {
    private data class Health(
        val avgMs: Double,
        val jitterMs: Double,
        val successes: Int,
        val consecutiveFailures: Int
    )
    private val samples = mutableMapOf<String, Health>()
    private var lastAutomaticSwitchMs = Long.MIN_VALUE
    private var lastManualSelectionMs = Long.MIN_VALUE

    /** Called only after a completed real all-server test batch. A 0 means not tested. */
    fun recordCompletedProbe(nodes: List<NoraNode>) {
        for (node in nodes) {
            val previous = samples[node.guid]
            val measured = node.latencyMs
            if (measured > 0L) {
                val avg = if (previous == null || previous.successes == 0) measured.toDouble()
                    else previous.avgMs * 0.7 + measured * 0.3
                val jitter = if (previous == null || previous.successes == 0) 0.0
                    else previous.jitterMs * 0.7 + abs(measured - previous.avgMs) * 0.3
                samples[node.guid] = Health(avg, jitter,
                    (previous?.successes ?: 0).plus(1).coerceAtMost(100),
                    0)
            } else if (measured < 0L) {
                samples[node.guid] = Health(
                    previous?.avgMs ?: 0.0,
                    previous?.jitterMs ?: 0.0,
                    previous?.successes ?: 0,
                    ((previous?.consecutiveFailures ?: 0) + 1).coerceAtMost(10)
                )
            }
        }
        // Do not keep historical GUIDs indefinitely after subscription replacements.
        val available = nodes.mapTo(hashSetOf()) { it.guid }
        samples.keys.retainAll(available)
    }

    /** An explicit user choice always has priority over automatic failover. */
    fun recordManualChoice(nowMs: Long) { lastManualSelectionMs = nowMs }
    fun networkChanged() {
        // Old-network failures must not be treated as evidence on the new network.
        samples.replaceAll { _, health -> health.copy(consecutiveFailures = 0) }
    }

    private fun score(node: NoraNode): Double {
        if (node.latencyMs <= 0L) return Double.MAX_VALUE
        val health = samples[node.guid]
        return if (health != null && health.successes > 0)
            health.avgMs + health.jitterMs * 1.4 + health.consecutiveFailures * 250
        else node.latencyMs.toDouble()
    }

    fun bestRoute(nodes: List<NoraNode>, countryCode: String?): NoraNode? {
        val allowed = if (countryCode == null) nodes
            else nodes.filter { it.countryCode == countryCode }
        if (allowed.isEmpty()) return null
        return allowed.filter { it.latencyMs > 0L }
            .minWithOrNull(compareBy<NoraNode> { score(it) }.thenBy { it.guid })
            ?: NoraRouteSelector.rank(allowed).firstOrNull()
    }

    /**
     * Never hand off solely for improved ping. Requires two independent negative
     * native full-test results, a positive tested alternative, and 5-minute
     * post-manual/auto switch protection. Restrict to the manually chosen country.
     */
    fun failoverCandidate(
        nodes: List<NoraNode>,
        currentGuid: String?,
        preferredCountry: String?,
        nowMs: Long
    ): NoraNode? {
        val current = nodes.firstOrNull { it.guid == currentGuid } ?: return null
        if (current.latencyMs >= 0L) return null
        if ((samples[current.guid]?.consecutiveFailures ?: 0) < 2) return null
        if (lastManualSelectionMs != Long.MIN_VALUE &&
            nowMs >= lastManualSelectionMs && nowMs - lastManualSelectionMs < 300_000L) return null
        if (lastAutomaticSwitchMs != Long.MIN_VALUE &&
            nowMs >= lastAutomaticSwitchMs && nowMs - lastAutomaticSwitchMs < 300_000L) return null
        val alternatives = nodes.filter {
            it.guid != current.guid && it.latencyMs > 0L &&
                (preferredCountry == null || it.countryCode == preferredCountry)
        }
        return bestRoute(alternatives, preferredCountry)
    }

    fun recordAutomaticSwitch(nowMs: Long) { lastAutomaticSwitchMs = nowMs }
}

/**
 * Network changes only request a new real server test; they never directly
 * restart the VPN. Observe Wi-Fi/cellular outside the VPN tunnel while visible.
 */
/**
 * registerNetworkCallback reports ALL matching underlying networks concurrently.
 * A late mobile-data callback must not override already available Wi-Fi.
 */
internal object NoraTransportSelection {
    fun preferred(transports: Collection<String>): String? =
        listOf("ethernet", "wifi", "cellular").firstOrNull { it in transports }
}

internal class NoraNetworkObserver(
    context: Context,
    private val onTransportChanged: () -> Unit
) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private var previousTransport: String? = null
    private var registered = false
    private var epoch = 0
    // Updated exclusively on the main Handler. Android may report several
    // physical networks, even when the app has only one effective route.
    private val networkTransports = mutableMapOf<Network, String>()

    private fun dispatchIfEffectiveNetworkChanged() {
        val next = NoraTransportSelection.preferred(networkTransports.values)
        val previous = previousTransport
        previousTransport = next
        if (previous != null && next != null && previous != next) onTransportChanged()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> null
            }
            val capturedEpoch = epoch
            main.post {
                if (!registered || capturedEpoch != epoch) return@post
                if (transport == null) networkTransports.remove(network)
                else networkTransports[network] = transport
                dispatchIfEffectiveNetworkChanged()
            }
        }

        override fun onLost(network: Network) {
            val capturedEpoch = epoch
            main.post {
                if (!registered || capturedEpoch != epoch) return@post
                networkTransports.remove(network)
                dispatchIfEffectiveNetworkChanged()
            }
        }
    }

    fun start() {
        if (registered) return
        epoch++
        networkTransports.clear()
        previousTransport = null
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity.registerNetworkCallback(request, callback)
            registered = true
        }
    }

    fun stop() {
        epoch++ // Drop callbacks already queued before unregistration.
        if (registered) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            registered = false
        }
        networkTransports.clear()
        previousTransport = null
    }
}
