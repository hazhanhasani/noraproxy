package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraRouteSelectorTest {
    @Test fun isoCoverageIncludesCountriesAndTerritories() {
        assertTrue(NoraRouteSelector.supportedCountryCount >= 240)
    }

    @Test fun recognizesNamesFlagsCodesAndVariants() {
        val cases = mapOf(
            "🇩🇪 Germany" to "DE",
            "🇫🇷 Paris" to "FR",
            "Canada Toronto" to "CA",
            "سرور ژاپن" to "JP",
            "سرور آلمان 02" to "DE",
            "UK London" to "GB",
            "USA New York" to "US",
            "🇧🇷 Brasil" to "BR",
            "NZ Auckland" to "NZ",
            "🇿🇦 South Africa" to "ZA",
            "🇵🇹 Portugal" to "PT",
            "🇲🇦 Morocco" to "MA",
            "🇮🇸 Iceland" to "IS",
            "🇰🇪 Kenya" to "KE",
            "🇻🇳 Vietnam" to "VN",
            "🇽🇰 Kosovo" to "XK"
        )
        cases.forEach { (name, code) ->
            assertEquals(name, code, NoraRouteSelector.identify(name).code)
        }
    }

    @Test fun flagsOverrideConflictingName() {
        assertEquals("DE", NoraRouteSelector.identify("🇩🇪 USA premium").code)
    }

    @Test fun unknownLocationsRemainDistinct() {
        assertEquals("UNKNOWN:first", NoraRouteSelector.identify("server alpha", "first").code)
        assertEquals("UNKNOWN:second", NoraRouteSelector.identify("server beta", "second").code)
        assertNotEquals("US", NoraRouteSelector.identify("useless server").code)
    }
}
