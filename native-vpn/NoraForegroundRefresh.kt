package com.v2ray.ang.ui.main

import android.os.SystemClock
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Guard repeated foreground callbacks (including ad and permission activities).
 * Elapsed realtime is monotonic; in-flight calls never overlap.
 */
internal class NoraForegroundRefreshGate(private val intervalMs: Long = 30_000L) {
    private var inFlight = false
    private var lastStart: Long? = null

    @Synchronized
    fun tryStart(nowMs: Long): Boolean {
        if (inFlight) return false
        val previous = lastStart
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        inFlight = true
        lastStart = nowMs
        return true
    }

    @Synchronized
    fun finish() { inFlight = false }
}

/** Sync enabled URL-backed groups on cold launch and on return to the foreground. */
internal object NoraForegroundSubscriptions {
    private val gate = NoraForegroundRefreshGate()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Raw/local groups and disabled remote groups must never be overwritten. */
    fun eligibleGroupIds(groups: List<SubscriptionCache>): List<String> =
        groups.filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
            .map { it.guid }

    suspend fun refresh(onFinished: suspend () -> Unit) {
        if (!gate.tryStart(SystemClock.elapsedRealtime())) return
        _running.value = true
        try {
            val ids = withContext(Dispatchers.IO) {
                eligibleGroupIds(MmkvManager.decodeSubscriptions())
            }
            if (ids.isEmpty()) return

            // Sequential writes avoid concurrent replacement of MMKV server lists.
            // A failed URL must not prevent updates of later groups.
            withContext(Dispatchers.IO) {
                ids.forEach { id ->
                    val current = MmkvManager.decodeSubscription(id) ?: return@forEach
                    if (!current.enabled || current.url.isBlank()) return@forEach
                    try {
                        AngConfigManager.updateConfigViaSub(SubscriptionCache(id, current))
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        // Continue with remaining groups even if one URL fails.
                    }
                }
            }
            // The UI and quota metadata must reflect the new server lists.
            onFinished()
        } finally {
            _running.value = false
            gate.finish()
        }
    }
}
