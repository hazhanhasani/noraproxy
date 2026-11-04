package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraDiagnosticReaderTest {
    @Test fun removesProxyCredentialsAndAccountUrls() {
        val sample = "WARN: vless://user-123@example.com:443?security=tls " +
            "https://provider.example.com/sub?token=abc123 " +
            "password=topsecret bearer supersecret"
        val output = NoraDiagnosticReader.sanitize(sample)
        assertTrue(output.startsWith("WARN:"))
        for (secret in listOf("user-123", "provider.example.com", "topsecret", "supersecret")) {
            assertFalse("Sensitive data leaked: $secret", output.contains(secret))
        }
        assertTrue(output.contains("[REDACTED-CONFIG]"))
        assertTrue(output.contains("[REDACTED-URL]"))
    }

    @Test fun scrubsHeadersWithoutRemovingHelpfulStatus() {
        val output = NoraDiagnosticReader.sanitize(
            "ERROR: failed retry=2 authorization:ABC123 api_key=privateKey")
        assertTrue(output.contains("ERROR: failed retry=2"))
        assertFalse(output.contains("ABC123"))
        assertFalse(output.contains("privateKey"))
    }

    @Test fun capsOutputLengthAndRemovesControlCharacters() {
        val sample = "status=ok\u0000\u0007" + "a".repeat(9000)
        val output = NoraDiagnosticReader.sanitize(sample)
        assertFalse(output.contains("\u0000"))
        assertFalse(output.contains("\u0007"))
        assertTrue(output.length <= 1800)
    }
}
