package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraSubscriptionUsageTest {
    @Test fun parsesStandardSubscriptionUserInfo() {
        val info = NoraSubscriptionUsageReader.parse(
            "upload=1073741824; download=2147483648; total=6442450944; expire=1730000000"
        )!!
        assertEquals(3221225472L, info.used)
        assertEquals(3221225472L, info.remaining)
        assertEquals(0.5f, info.usagePercent()!!, 0.0001f)
        assertEquals("3.0 GB باقی‌مانده", info.remainingTrafficLabel())
    }

    @Test fun handlesMissingOrMalformedHeadersWithoutInventedValues() {
        assertNull(NoraSubscriptionUsageReader.parse(null))
        assertNull(NoraSubscriptionUsageReader.parse("Server: ok"))
        assertNull(NoraSubscriptionUsageReader.parse("total=broken; expire=no"))
        val info = NoraSubscriptionUsageReader.parse("total=4294967296")!!
        assertNull(info.remaining)
        assertEquals("مصرف نامشخص", info.remainingTrafficLabel())
        assertEquals("زمان نامشخص", info.remainingTimeLabel(100))
    }

    @Test fun zeroAndExpiredMetadataAreExplicit() {
        val unlimited = NoraSubscriptionUsageReader.parse(
            "UPLOAD=0; DOWNLOAD=0; TOTAL=0; EXPIRE=0"
        )!!
        assertEquals("حجم نامحدود", unlimited.remainingTrafficLabel())
        assertEquals("زمان نامحدود", unlimited.remainingTimeLabel(100))
        val expired = NoraSubscriptionUsageReader.parse("expire=100")!!
        assertEquals("منقضی شده", expired.remainingTimeLabel(101))
    }

    @Test fun clampsOverusedQuota() {
        val info = NoraSubscriptionUsageReader.parse("upload=8; download=7; total=10")!!
        assertEquals(0L, info.remaining)
        assertEquals(1.0f, info.usagePercent()!!, 0.0001f)
    }

    @Test fun calculatesRemainingDaysWithoutRoundingDown() {
        val info = NoraSubscriptionUsageReader.parse("expire=172800")!!
        assertEquals("2 روز باقی‌مانده", info.remainingTimeLabel(1))
        assertEquals("1 روز باقی‌مانده", info.remainingTimeLabel(86401))
    }

    @Test fun rejectsOutOfRangeNumbers() {
        assertNull(NoraSubscriptionUsageReader.parse("upload=99999999999999999999999"))
    }
}
