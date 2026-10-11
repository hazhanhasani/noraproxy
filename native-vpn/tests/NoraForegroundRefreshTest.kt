package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.*
import org.junit.Test

class NoraForegroundRefreshTest {
    @Test fun refreshesEveryEnabledRemoteGroupIncludingNonSelected() {
        val groups = listOf(
            SubscriptionCache("remote-a", SubscriptionItem(remarks = "A", url = "https://example.com/a", enabled = true, autoUpdate = false)),
            SubscriptionCache("raw-default", SubscriptionItem(remarks = "Default", url = "", enabled = true)),
            SubscriptionCache("inactive", SubscriptionItem(remarks = "Off", url = "https://example.com/off", enabled = false)),
            SubscriptionCache("remote-b", SubscriptionItem(remarks = "B", url = "https://example.com/b", enabled = true, autoUpdate = true))
        )
        assertEquals(listOf("remote-a", "remote-b"), NoraForegroundSubscriptions.eligibleGroupIds(groups))
    }

    @Test fun foregroundRefreshHasDebounceAndNoOverlappingRuns() {
        val gate = NoraForegroundRefreshGate(30_000L)
        assertTrue(gate.tryStart(100L))
        assertFalse(gate.tryStart(60_000L)) // an existing run is still active
        gate.finish()
        assertFalse(gate.tryStart(20_000L))
        assertTrue(gate.tryStart(30_100L))
        gate.finish()
        assertTrue(gate.tryStart(60_100L))
    }
}
