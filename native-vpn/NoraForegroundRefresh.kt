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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The foreground synchronizer and all user-triggered imports share one mutex.
 * Upstream's subscription replacement mutates global MMKV server lists.
 */
internal object NoraSubscriptionMutationLock {
    val mutex = Mutex()
}

/**
 * A refresh must not start while another one is running or immediately after
 * a slow completed refresh. elapsedRealtime is immune to wall-clock changes.
 */
internal class NoraForegroundRefreshGate(private val intervalMs: Long = 30_000L) {
    private var inFlight = false
    private var lastFinishedMs: Long? = null

    @Synchronized
    fun tryStart(nowMs: Long): Boolean {
        if (inFlight) return false
        val last = lastFinishedMs
        if (last != null && nowMs >= last && nowMs - last < intervalMs) return false
        inFlight = true
        return true
    }

    @Synchronized
    fun finish(nowMs: Long) {
        if (!inFlight) return
        inFlight = false
        lastFinishedMs = nowMs
    }
}

/** Aggregate counts only: never include subscription URLs, hostnames or credentials. */
internal data class NoraRefreshReport(
    val checked: Int,
    val succeeded: Int,
    val failed: Int,
    val fatal: Boolean = false
) {
    init {
        require(checked >= 0 && succeeded >= 0 && failed >= 0)
        require(succeeded + failed <= checked)
    }

    val message: String
        get() = when {
            fatal -> "همگام‌سازی اشتراک‌ها ناموفق بود؛ مجدداً تلاش کنید."
            checked == 0 -> "اشتراک فعالی برای همگام‌سازی وجود ندارد."
            failed == 0 -> "همگام‌سازی کامل: $succeeded از $checked گروه"
            else -> "همگام‌سازی: $succeeded موفق · $failed ناموفق از $checked گروه"
        }
}

/** Sync enabled URL-backed groups on cold launch and on return to foreground. */
internal object NoraForegroundSubscriptions {
    private val gate = NoraForegroundRefreshGate()
    private val _running = MutableStateFlow(false)
    private val _lastReport = MutableStateFlow<NoraRefreshReport?>(null)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    val lastReport: StateFlow<NoraRefreshReport?> = _lastReport.asStateFlow()

    /** Never overwrite raw/local groups or disabled remote subscriptions. */
    fun eligibleGroupIds(groups: List<SubscriptionCache>): List<String> =
        groups.filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
            .map { it.guid }.distinct()

    suspend fun refresh(onFinished: suspend () -> Unit) {
        if (!gate.tryStart(SystemClock.elapsedRealtime())) return
        _running.value = true
        var checked = 0
        var succeeded = 0
        var failed = 0
        try {
            NoraSubscriptionMutationLock.mutex.withLock {
                withContext(Dispatchers.IO) {
                    val ids = eligibleGroupIds(MmkvManager.decodeSubscriptions())
                    checked = ids.size
                    for (id in ids) {
                        val current = MmkvManager.decodeSubscription(id)
                        if (current == null || !current.enabled || current.url.isBlank()) {
                            failed++
                            continue
                        }
                        try {
                            val result = AngConfigManager.updateConfigViaSub(
                                SubscriptionCache(id, current)
                            )
                            if (result.successCount > 0) succeeded++ else failed++
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            // One failed URL must not block the remaining subscriptions.
                            failed++
                        }
                    }
                }
            }
            _lastReport.value = NoraRefreshReport(checked, succeeded, failed)
            if (checked > 0) onFinished()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            _lastReport.value = NoraRefreshReport(checked, succeeded, failed, fatal = true)
        } finally {
            _running.value = false
            gate.finish(SystemClock.elapsedRealtime())
        }
    }
}
