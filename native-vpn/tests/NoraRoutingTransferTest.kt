package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.*
import org.junit.Test

class NoraRoutingTransferTest {
    private fun item(name: String, locked: Boolean = false) = RulesetItem(
        id = name, remarks = name, domain = listOf("domain:example.com"),
        outboundTag = AppConfig.TAG_PROXY, locked = locked
    )

    @Test fun roundTripExportsOnlyEditableRules() {
        val current = listOf(item("protected", locked = true), item("editable"))
        val content = NoraRoutingTransfer.exportUnlocked(current)
        assertNotNull(content)
        assertFalse(content!!.contains("protected"))
        val imported = NoraRoutingTransfer.preview(content)
        assertTrue(imported is NoraRoutingTransfer.ImportResult.Ready)
        val entries = (imported as NoraRoutingTransfer.ImportResult.Ready).rules
        assertEquals(1, entries.size)
        assertEquals("editable", entries.first().remarks)
        assertNotEquals("editable", entries.first().id)
        assertEquals(false, entries.first().locked)
    }

    @Test fun invalidInputsNeverProduceReadyRules() {
        val inputs = listOf(
            null, "", "not-json", "[]",
            "x".repeat(256_001),
            JsonUtil.toJson(listOf(item("protected", locked = true))),
            JsonUtil.toJson(listOf(RulesetItem(
                remarks = "block the internet", outboundTag = AppConfig.TAG_BLOCKED
            )))
        )
        for (input in inputs) {
            assertTrue("Unexpected accepted input: ${input?.take(20)}",
                NoraRoutingTransfer.preview(input) is NoraRoutingTransfer.ImportResult.Invalid)
        }
    }

    @Test fun protectedRulesArePreservedAndOriginalsUntouched() {
        val protected = item("protected", locked = true)
        val editable = item("editable")
        val next = item("replacement")
        val combined = NoraRoutingTransfer.replaceUnlocked(
            listOf(protected, editable), listOf(next)
        )
        assertEquals(2, combined.size)
        assertEquals("protected", combined[0].id)
        assertEquals(true, combined[0].locked)
        assertEquals("replacement", combined[1].id)
        assertEquals(false, combined[1].locked)
        assertEquals("editable", editable.id)
        assertEquals(true, protected.locked)
    }

    @Test fun generatedIdsDoNotCollideAfterImport() {
        val text = JsonUtil.toJson(listOf(item("same"), item("same")))
        val result = NoraRoutingTransfer.preview(text)
        assertTrue(result is NoraRoutingTransfer.ImportResult.Ready)
        val entries = (result as NoraRoutingTransfer.ImportResult.Ready).rules
        assertNotEquals(entries[0].id, entries[1].id)
    }

    @Test fun safeMtuAcceptsDefaultAndValidMobileRange() {
        assertNull(NoraAdvancedValidation.validateVpnMtu(""))
        assertNull(NoraAdvancedValidation.validateVpnMtu("1280"))
        assertNull(NoraAdvancedValidation.validateVpnMtu("1500"))
        for (invalid in listOf("-1", "0", "1279", "1501", "9000", "abc", "12345")) {
            assertNotNull(invalid, NoraAdvancedValidation.validateVpnMtu(invalid))
        }
    }
}
