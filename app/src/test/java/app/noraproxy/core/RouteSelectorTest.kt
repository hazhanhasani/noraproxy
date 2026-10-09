package app.noraproxy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSelectorTest {
    private fun node(uri: String, title: String, region: String): ProxyNode =
        ProxyNode(stableNodeId(uri), uri, "VLESS", "example.org", 443, title,
            Region(region, title, "🌐"))

    @Test fun unreachableNeverWins() {
        val failed = node("vless://a@example.org:443", "A", "de")
        val good = node("vless://b@example.org:443", "B", "nl")
        val result = RouteSelector.rank(
            listOf(failed, good),
            mapOf(failed.id to ProbeStats(emptyList(), 1),
                good.id to ProbeStats(listOf(210), 1))
        )
        assertEquals(good.id, result.first().node.id)
        assertFalse(result.last().stats.reachable)
    }

    @Test fun unreliableFastNodeLosesToStableNode() {
        val unstable = node("vless://a@example.org:443", "A", "de")
        val reliable = node("vless://b@example.org:443", "B", "nl")
        val result = RouteSelector.rank(
            listOf(unstable, reliable),
            mapOf(unstable.id to ProbeStats(listOf(20), 3),
                reliable.id to ProbeStats(listOf(75, 80, 85), 3))
        )
        assertEquals(reliable.id, result.first().node.id)
    }

    @Test fun regionsAreUniqueAndOnlyReachable() {
        val one = node("vless://a@example.org:443", "Germany 1", "de")
        val two = node("vless://b@example.org:443", "Germany 2", "de")
        val dead = node("vless://c@example.org:443", "Netherlands", "nl")
        val ranked = RouteSelector.rank(listOf(one, two, dead),
            mapOf(one.id to ProbeStats(listOf(90), 1),
                two.id to ProbeStats(listOf(40), 1),
                dead.id to ProbeStats(emptyList(), 1)))
        val perCountry = RouteSelector.bestPerRegion(ranked)
        assertEquals(1, perCountry.size)
        assertEquals(two.id, perCountry.single().node.id)
        assertTrue(perCountry.single().stats.reachable)
    }
}
