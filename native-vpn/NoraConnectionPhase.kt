package com.v2ray.ang.ui.main

/**
 * A presentation-only state machine. Never assumes that tapping the power
 * button starts a VPN: only the observed native service state may be CONNECTED.
 * Pending actions can time out without losing the actual service state.
 */
internal enum class NoraConnectionPhase(
    val statusLabel: String,
    val headline: String,
    val actionHint: String
) {
    NO_ROUTE("اشتراک موجود نیست", "ابتدا اشتراک اضافه کنید", "از بخش اشتراک، یک لینک یا کانفیگ وارد کنید"),
    READY("آماده اتصال", "با یک لمس متصل شوید", "برای اتصال لمس کنید"),
    CONNECTING("در حال برقراری اتصال", "در انتظار پاسخ سرویس VPN", "وضعیت اتصال در حال بررسی است"),
    CONNECTED("سرویس VPN روشن است", "سرویس VPN در حال اجراست", "برای قطع اتصال لمس کنید"),
    DISCONNECTING("در حال قطع اتصال", "در انتظار توقف سرویس VPN", "وضعیت قطع اتصال در حال بررسی است"),
    FAILED("اتصال تأیید نشد", "سرویس VPN روشن نشد", "برای تلاش دوباره لمس کنید");

    val isBusy: Boolean
        get() = this == CONNECTING || this == DISCONNECTING

    companion object {
        /**
         * [isRunning] is read from native ViewModel.uiState. [expectedRunning]
         * is merely the last requested target, never evidence of connectivity.
         */
        fun resolve(
            isRunning: Boolean,
            expectedRunning: Boolean?,
            hasRoute: Boolean,
            timedOut: Boolean
        ): NoraConnectionPhase {
            if (expectedRunning != null && isRunning != expectedRunning) {
                return if (expectedRunning) CONNECTING else DISCONNECTING
            }
            // Native service state outranks stale pending and timeout flags.
            if (isRunning) return CONNECTED
            if (timedOut) return FAILED
            return if (hasRoute) READY else NO_ROUTE
        }
    }
}
