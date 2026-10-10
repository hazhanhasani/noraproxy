package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraImportRouterTest {
    @Test fun httpsSubscriptionIsNotImportedAsNewGroup() {
        val input = NoraImportRouter.classify("https://sub.example.com/123?token=abc")
        assertTrue(input is NoraImportPayload.Subscription)
        assertEquals("https://sub.example.com/123?token=abc",
            (input as NoraImportPayload.Subscription).url)
    }

    @Test fun rawNodesRemainInSelectedLocalGroup() {
        val nodes = "vless://uuid@example.com:443#DE\n" +
            "trojan://secret@example.com:443#NL\n" +
            "ss://example"
        val input = NoraImportRouter.classify(nodes)
        assertTrue(input is NoraImportPayload.Raw)
        assertEquals(nodes, (input as NoraImportPayload.Raw).text)
    }

    @Test fun mixedUrlsAndRawConfigsAreRejected() {
        val input = NoraImportRouter.classify(
            "https://sub.example.com/user\nvless://uuid@example.com:443"
        )
        assertTrue(input is NoraImportPayload.Invalid)
    }

    @Test fun multipleSubscriptionsRequireSeparateGroups() {
        val input = NoraImportRouter.classify(
            "https://sub.example.com/1\nhttps://sub.example.com/2"
        )
        assertTrue(input is NoraImportPayload.Invalid)
    }

    @Test fun rejectsInsecureOrLocalSubscriptionUrls() {
        assertTrue(NoraImportRouter.classify("http://sub.example.com") is NoraImportPayload.Invalid)
        assertTrue(NoraImportRouter.classify("https://127.0.0.1/sub") is NoraImportPayload.Invalid)
        assertTrue(NoraImportRouter.classify("https://localhost/sub") is NoraImportPayload.Invalid)
        assertTrue(NoraImportRouter.classify("https://user:pass@example.com/sub") is NoraImportPayload.Invalid)
    }

    @Test fun rejectsInvalidInputWithoutCreatingAGroup() {
        assertTrue(NoraImportRouter.classify("not a config") is NoraImportPayload.Invalid)
        assertTrue(NoraImportRouter.classify("") is NoraImportPayload.Invalid)
    }
}
