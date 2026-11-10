package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NoraForegroundRefreshTest {
    @Test fun refreshesEveryEnabledRemoteGroupIncludingNonSelected() {
        val groups = listOf(
            SubscriptionCache("remote-a", SubscriptionItem(remarks = "A", url = "https://example.com/a", enabled = true, autoUpdate = false)),
            SubscriptionCache("raw-default", SubscriptionItem(remarks = "Default", url = "", enabled = true)),
            SubscriptionCache("inactive", SubscriptionItem(remarks = "Off", url = "https://example.com/off", enabled = false)),
            SubscriptionCache("remote-b", SubscriptionItem(remarks = "B", url = "https://example.com/b", enabled = true, autoUpdate = true)),
            SubscriptionCache("remote-a", SubscriptionItem(remarks = "A duplicated", url = "https://example.com/a", enabled = true))
        )
        assertEquals(listOf("remote-a", "remote-b"), NoraForegroundSubscriptions.eligibleGroupIds(groups))
    }

    @Test fun cooldownBeginsAfterRefreshFinishesAndNoRunsOverlap() {
        val gate = NoraForegroundRefreshGate(30_000L)
        assertTrue(gate.tryStart(100L))
        assertFalse(gate.tryStart(60_000L)) // Still running: never overlap.
        gate.finish(60_000L) // A slow request must not trigger another one immediately.
        assertFalse(gate.tryStart(89_999L))
        assertTrue(gate.tryStart(90_000L))
        gate.finish(95_000L)
        assertFalse(gate.tryStart(124_999L))
        assertTrue(gate.tryStart(125_000L))
    }

    @Test fun monotonicClockResetDoesNotPermanentlyBlockRefresh() {
        val gate = NoraForegroundRefreshGate()
        assertTrue(gate.tryStart(50_000L))
        gate.finish(55_000L)
        assertTrue(gate.tryStart(1_000L))
        gate.finish(1_100L)
    }

    @Test fun statusReportsOnlyAggregateSafeInformation() {
        val report = NoraRefreshReport(3, 2, 1)
        assertEquals("همگام‌سازی: 2 موفق · 1 ناموفق از 3 گروه", report.message)
        assertEquals("همگام‌سازی کامل: 2 از 2 گروه", NoraRefreshReport(2, 2, 0).message)
        assertEquals("اشتراک فعالی برای همگام‌سازی وجود ندارد.", NoraRefreshReport(0, 0, 0).message)
        assertTrue(NoraRefreshReport(2, 1, 1, fatal = true).message.contains("ناموفق"))
        assertFalse(report.message.contains("https://"))
    }

    @Test fun impossibleStatusCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            NoraRefreshReport(2, 2, 1)
        }
    }
}
