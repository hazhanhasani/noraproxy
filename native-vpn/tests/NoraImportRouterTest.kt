package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraImportRouterTest {
    @Test fun oneSubscriptionCreatesOneAutomaticGroupInput() {
        val input = NoraImportRouter.classify("https://sub.example.com/a?token=123")
        assertTrue(input is NoraImportPayload.Subscriptions)
        assertEquals(listOf("https://sub.example.com/a?token=123"),
            (input as NoraImportPayload.Subscriptions).urls)
    }

    @Test fun multipleSubscriptionLinksCreateIndependentGroupInputs() {
        val input = NoraImportRouter.classify(
            "https://sub.example.com/one\nhttps://sub.example.com/two"
        )
        assertTrue(input is NoraImportPayload.Subscriptions)
        assertEquals(2, (input as NoraImportPayload.Subscriptions).urls.size)
    }

    @Test fun duplicateUrlsAreDeduplicatedBeforeGroupCreation() {
        val input = NoraImportRouter.classify(
            "https://example.com/one\nhttps://example.com/one"
        ) as NoraImportPayload.Subscriptions
        assertEquals(1, input.urls.size)
    }

    @Test fun rawNodesRequireNoSelectedGroup() {
        val nodes = "vless://uuid@example.com:443#DE\n" +
            "trojan://secret@example.com:443#NL\nss://example"
        val input = NoraImportRouter.classify(nodes)
        assertTrue(input is NoraImportPayload.Raw)
        assertEquals(nodes, (input as NoraImportPayload.Raw).text)
    }

    @Test fun mixedUrlsAndRawConfigsAreRejected() {
        assertTrue(NoraImportRouter.classify(
            "https://sub.example.com/user\nvless://uuid@example.com:443"
        ) is NoraImportPayload.Invalid)
    }

    @Test fun insecureAndLoopbackSubscriptionsAreRejected() {
        for (url in listOf(
            "http://example.com/sub", "https://127.0.0.1/sub",
            "https://localhost/sub", "https://user:pass@example.com/sub",
            "https://example.local/sub"
        )) {
            assertTrue("Should reject $url", NoraImportRouter.classify(url) is NoraImportPayload.Invalid)
        }
    }

    @Test fun rejectsInvalidAndEmptyInputs() {
        assertTrue(NoraImportRouter.classify("not a config") is NoraImportPayload.Invalid)
        assertTrue(NoraImportRouter.classify("") is NoraImportPayload.Invalid)
    }

    @Test fun maximumTwentySubscriptionLinksPerImport() {
        val urls = (1..21).joinToString("\n") { "https://sub.example.com/$it" }
        assertTrue(NoraImportRouter.classify(urls) is NoraImportPayload.Invalid)
    }
}
