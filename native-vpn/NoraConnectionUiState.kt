package com.v2ray.ang.ui.main

/** Display-only states. Only the native VPN service determines [actualRunning]. */
internal enum class NoraConnectionPhase {
    NO_SUBSCRIPTION, DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING,
    CONNECTION_TIMEOUT, DISCONNECTION_TIMEOUT
}

/** Pure state projection: it never starts VPN or assumes a button tap succeeded. */
internal data class NoraConnectionUiState(
    val phase: NoraConnectionPhase,
    val actualRunning: Boolean,
    val hasRoute: Boolean
) {
    val isBusy: Boolean get() = phase == NoraConnectionPhase.CONNECTING ||
        phase == NoraConnectionPhase.DISCONNECTING
    val canToggle: Boolean get() = !isBusy && (actualRunning || hasRoute)

    val statusLabel: String get() = when (phase) {
        NoraConnectionPhase.NO_SUBSCRIPTION -> "اشتراک فعال وجود ندارد"
        NoraConnectionPhase.DISCONNECTED -> "آماده اتصال"
        NoraConnectionPhase.CONNECTING -> "در انتظار اتصال سرویس"
        NoraConnectionPhase.CONNECTED -> "تونل VPN فعال است"
        NoraConnectionPhase.DISCONNECTING -> "در انتظار قطع سرویس"
        NoraConnectionPhase.CONNECTION_TIMEOUT -> "اتصال هنوز تأیید نشده"
        NoraConnectionPhase.DISCONNECTION_TIMEOUT -> "قطع اتصال هنوز تأیید نشده"
    }

    val headline: String get() = when (phase) {
        NoraConnectionPhase.NO_SUBSCRIPTION -> "ابتدا یک اشتراک اضافه کنید"
        NoraConnectionPhase.DISCONNECTED -> "با یک لمس متصل شوید"
        NoraConnectionPhase.CONNECTING -> "در حال بررسی وضعیت اتصال"
        NoraConnectionPhase.CONNECTED -> "اتصال امن شما فعال است"
        NoraConnectionPhase.DISCONNECTING -> "در حال بررسی قطع اتصال"
        NoraConnectionPhase.CONNECTION_TIMEOUT -> "اتصال پاسخ نداد"
        NoraConnectionPhase.DISCONNECTION_TIMEOUT -> "قطع اتصال پاسخ نداد"
    }

    val actionHint: String get() = when (phase) {
        NoraConnectionPhase.NO_SUBSCRIPTION -> "برای شروع، لینک اشتراک یا کانفیگ وارد کنید"
        NoraConnectionPhase.DISCONNECTED -> "برای اتصال لمس کنید"
        NoraConnectionPhase.CONNECTING, NoraConnectionPhase.DISCONNECTING ->
            "منتظر پاسخ واقعی سرویس VPN بمانید"
        NoraConnectionPhase.CONNECTED -> "برای قطع اتصال لمس کنید"
        NoraConnectionPhase.CONNECTION_TIMEOUT -> "وضعیت سرویس را بررسی کنید و دوباره تلاش کنید"
        NoraConnectionPhase.DISCONNECTION_TIMEOUT -> "قطع اتصال را دوباره امتحان کنید"
    }

    companion object {
        /** Timeout is an unconfirmed outcome, not proof that the VPN failed. */
        fun resolve(
            isRunning: Boolean,
            pendingTarget: Boolean?,
            timedOutTarget: Boolean?,
            hasRoute: Boolean
        ): NoraConnectionUiState {
            val phase = when {
                pendingTarget != null && pendingTarget != isRunning ->
                    if (pendingTarget) NoraConnectionPhase.CONNECTING else NoraConnectionPhase.DISCONNECTING
                timedOutTarget != null && timedOutTarget != isRunning ->
                    if (timedOutTarget) NoraConnectionPhase.CONNECTION_TIMEOUT
                    else NoraConnectionPhase.DISCONNECTION_TIMEOUT
                isRunning -> NoraConnectionPhase.CONNECTED
                !hasRoute -> NoraConnectionPhase.NO_SUBSCRIPTION
                else -> NoraConnectionPhase.DISCONNECTED
            }
            return NoraConnectionUiState(phase, isRunning, hasRoute)
        }
    }
}
