package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraSmartEngineTest {
    private fun node(guid: String, country: String, ping: Long) =
        NoraNode(guid, country, country, "🌐", ping)

    @Test fun selectsLowestMeasuredLatencyWithoutUnknowns() {
        val smart = NoraSmartEngine()
        assertEquals("fast", smart.bestRoute(listOf(
            node("unknown", "DE", 0), node("slow", "DE", 220), node("fast", "DE", 60)
        ), null)?.guid)
    }

    @Test fun neverCrossesManuallyChosenCountry() {
        val smart = NoraSmartEngine()
        assertEquals("german", smart.bestRoute(listOf(
            node("usa", "US", 15), node("german", "DE", 70)
        ), "DE")?.guid)
        assertNull(smart.bestRoute(listOf(node("usa", "US", 15)), "DE"))
    }

    @Test fun oneFailedTestOrUnknownPingNeverTriggersFailover() {
        val smart = NoraSmartEngine()
        val oneFail = listOf(node("current", "DE", -1), node("backup", "DE", 42))
        smart.recordCompletedProbe(oneFail)
        assertNull(smart.failoverCandidate(oneFail, "current", "DE", 900_000))
        assertNull(smart.failoverCandidate(listOf(
            node("current", "DE", 0), node("backup", "DE", 42)
        ), "current", "DE", 900_000))
    }

    @Test fun repeatedNativeFailuresAllowHealthyAlternativeInSameCountry() {
        val smart = NoraSmartEngine()
        val tested = listOf(node("bad", "DE", -1), node("backup", "DE", 42),
            node("foreign", "US", 12))
        smart.recordCompletedProbe(tested)
        smart.recordCompletedProbe(tested)
        assertEquals("backup", smart.failoverCandidate(
            tested, "bad", "DE", 900_000
        )?.guid)
        assertEquals("foreign", smart.failoverCandidate(
            tested, "bad", null, 900_000
        )?.guid)
    }

    @Test fun manualChoiceAndAutomaticSwitchPreventConnectionChurn() {
        val smart = NoraSmartEngine()
        val tested = listOf(node("bad", "DE", -1), node("backup", "DE", 42))
        smart.recordCompletedProbe(tested)
        smart.recordCompletedProbe(tested)
        smart.recordManualChoice(100_000)
        assertNull(smart.failoverCandidate(tested, "bad", null, 100_100))
        assertEquals("backup", smart.failoverCandidate(tested, "bad", null, 401_000)?.guid)
        smart.recordAutomaticSwitch(401_000)
        assertNull(smart.failoverCandidate(tested, "bad", null, 402_000))
    }

    @Test fun networkChangeRequiresFreshFailureEvidence() {
        val smart = NoraSmartEngine()
        val tested = listOf(node("bad", "DE", -1), node("backup", "DE", 42))
        smart.recordCompletedProbe(tested)
        smart.recordCompletedProbe(tested)
        smart.networkChanged()
        assertNull(smart.failoverCandidate(tested, "bad", null, 900_000))
    }

    @Test fun jitterAndHistoryAffectPreferenceWithoutForcingFailover() {
        val smart = NoraSmartEngine()
        smart.recordCompletedProbe(listOf(node("stable", "DE", 65), node("spiky", "DE", 40)))
        smart.recordCompletedProbe(listOf(node("stable", "DE", 66), node("spiky", "DE", 190)))
        assertEquals("stable", smart.bestRoute(listOf(
            node("stable", "DE", 66), node("spiky", "DE", 190)
        ), null)?.guid)
    }
}
