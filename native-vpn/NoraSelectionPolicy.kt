package com.v2ray.ang.ui.main

/** Pure route-selection safety contract for the pinned MainActivity integration. */
internal object NoraSelectionPolicy {
    fun shouldChange(currentGuid: String?, nextGuid: String): Boolean =
        nextGuid.isNotBlank() && currentGuid != nextGuid

    /** MainActivity.SelectServer restarts Xray; only use while already connected. */
    fun mustRestart(isRunning: Boolean): Boolean = isRunning
}
