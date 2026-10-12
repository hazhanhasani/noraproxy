package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraSelectionPolicyTest {
    @Test fun offlineSelectionPersistsWithoutAttemptingToStartVpn() {
        assertTrue(NoraSelectionPolicy.shouldChange("server-a", "server-b"))
        assertFalse(NoraSelectionPolicy.mustRestart(false))
    }

    @Test fun onlineRouteSelectionRequiresNativeServiceRestart() {
        assertTrue(NoraSelectionPolicy.mustRestart(true))
    }

    @Test fun repeatOrBlankGuidNeverChangesSelection() {
        assertFalse(NoraSelectionPolicy.shouldChange("same", "same"))
        assertFalse(NoraSelectionPolicy.shouldChange(null, ""))
    }
}
