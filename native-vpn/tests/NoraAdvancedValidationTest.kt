package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.*
import org.junit.Test

class NoraAdvancedValidationTest {
    @Test fun requiresNamedScopedRule() {
        assertNotNull(NoraAdvancedValidation.validateRule(
            "", "", "", "", "", "", "", AppConfig.TAG_PROXY))
        assertNotNull(NoraAdvancedValidation.validateRule(
            "Block all", "", "", "", "", "", "", AppConfig.TAG_BLOCKED))
        assertNull(NoraAdvancedValidation.validateRule(
            "Local DNS", "domain:example.com,geosite:private", "", "", "",
            "443,1000-2000", "tcp", AppConfig.TAG_DIRECT))
    }

    @Test fun validatesPortRangesAndCsvBoundaries() {
        assertTrue(NoraAdvancedValidation.validatePorts("443,80,1000-2000"))
        assertFalse(NoraAdvancedValidation.validatePorts("0"))
        assertFalse(NoraAdvancedValidation.validatePorts("65536"))
        assertFalse(NoraAdvancedValidation.validatePorts("300-100"))
        assertFalse(NoraAdvancedValidation.validatePorts("abc"))
        assertNull(NoraAdvancedValidation.csv("x".repeat(5000)))
        assertEquals(listOf("domain:a", "domain:b"),
            NoraAdvancedValidation.csv("domain:a,domain:a, domain:b"))
    }

    @Test fun preservesStableIdentifiersAndLockStateDuringEdits() {
        val original = RulesetItem(
            id = "stable-id", remarks = "Existing", domain = listOf("domain:old.example"),
            outboundTag = AppConfig.TAG_PROXY, enabled = false, locked = true
        )
        val updated = NoraAdvancedValidation.buildRule(
            original, "Edited", "domain:new.example", "", "", "",
            "443", "", AppConfig.TAG_DIRECT
        )
        assertEquals("stable-id", updated.id)
        assertFalse(updated.enabled)
        assertEquals(true, updated.locked)
        assertEquals("Edited", updated.remarks)
        assertEquals(listOf("domain:new.example"), updated.domain)
        assertEquals(AppConfig.TAG_DIRECT, updated.outboundTag)
        assertEquals("Existing", original.remarks)
    }

    @Test fun givesNewRulesDistinctIds() {
        val first = NoraAdvancedValidation.buildRule(
            null, "First", "domain:example.com", "", "", "", "", "", AppConfig.TAG_PROXY)
        val second = NoraAdvancedValidation.buildRule(
            null, "Second", "domain:example.com", "", "", "", "", "", AppConfig.TAG_PROXY)
        assertTrue(first.id.isNotBlank())
        assertNotEquals(first.id, second.id)
    }

    @Test fun validatesDnsFormatsWithoutNetworkLookup() {
        assertNull(NoraAdvancedValidation.validateDnsCsv(
            "1.1.1.1,https://cloudflare-dns.com/dns-query,tcp://dns.example.com:853", false))
        assertNull(NoraAdvancedValidation.validateDnsCsv(
            "1.1.1.1,8.8.8.8", true))
        assertNotNull(NoraAdvancedValidation.validateDnsCsv(
            "https://cloudflare-dns.com/dns-query", true))
        assertNotNull(NoraAdvancedValidation.validateDnsCsv(
            "http://dns.example.com/dns-query", false))
        assertNotNull(NoraAdvancedValidation.validateDnsCsv(
            "https://user:password@dns.example.com/dns-query", false))
        assertNotNull(NoraAdvancedValidation.validateDnsCsv(
            "https://dns.example.com:99999/dns-query", false))
        assertNotNull(NoraAdvancedValidation.validateDnsCsv(
            "javascript:alert(1)", false))
        assertNull(NoraAdvancedValidation.validateDnsCsv("", false))
        assertEquals("1.1.1.1,8.8.8.8",
            NoraAdvancedValidation.normalizedDnsCsv("1.1.1.1, 8.8.8.8"))
        assertEquals("https://dns.example.com/dns-query",
            NoraAdvancedValidation.normalizedDnsCsv("HTTPS://dns.example.com/dns-query"))
    }
}
