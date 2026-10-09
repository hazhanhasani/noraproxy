package app.noraproxy.core

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {
    @Test fun parsesMixedConfigsAndDeduplicates() {
        val vmess = Base64.getEncoder().encodeToString(
            """{"add":"vm.example.com","port":"443","ps":"🇳🇱 Netherlands"}""".toByteArray()
        )
        val input = """
            vless://550e8400-e29b-41d4-a716-446655440000@de.example.com:443?encryption=none#%F0%9F%87%A9%F0%9F%87%AA%20Germany
            trojan://secret@fr.example.com:443#France
            vmess://$vmess
            vless://550e8400-e29b-41d4-a716-446655440000@de.example.com:443?encryption=none#%F0%9F%87%A9%F0%9F%87%AA%20Germany
        """.trimIndent()
        val nodes = SubscriptionParser.parse(input)
        assertEquals(3, nodes.size)
        assertEquals("de", nodes.first().region.key)
        assertEquals("nl", nodes.last().region.key)
    }

    @Test fun supportsBase64Subscription() {
        val source = "vless://id@a.example.com:443#Germany\n"
        val wrapped = Base64.getEncoder().encodeToString(source.toByteArray())
        assertEquals(1, SubscriptionParser.parse(wrapped).size)
    }

    @Test fun parsesShadowsocksAndSkipsBadNodes() {
        val user = Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        val input = "ss://" + user + "@sg.example.com:8388#Singapore\n" +
            "vless://bad@host:0#Invalid\n" +
            "something://wrong"
        val result = SubscriptionParser.parse(input)
        assertEquals(1, result.size)
        assertEquals("sg.example.com", result[0].host)
        assertEquals("sg", result[0].region.key)
    }

    @Test fun noFabricatedFallbackForGarbage() {
        assertTrue(SubscriptionParser.parse("garbage text").isEmpty())
        assertFalse(SubscriptionParser.parse("trojan://pwd@example.com:443#Japan").isEmpty())
    }
}
