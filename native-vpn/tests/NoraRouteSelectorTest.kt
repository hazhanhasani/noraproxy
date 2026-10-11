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

    @Test fun searchesPersianEnglishAliasesAndIsoCodes() {
        val germany = NoraNode("de", "DE", "آلمان", "🇩🇪", 50L)
        val france = NoraNode("fr", "FR", "فرانسه", "🇫🇷", 25L)
        val canada = NoraNode("ca", "CA", "کانادا", "🇨🇦", 70L)
        val all = listOf(france, germany, canada)
        assertEquals(listOf(germany), NoraRouteSelector.searchLocations(all, "germany", emptySet()))
        assertEquals(listOf(france), NoraRouteSelector.searchLocations(all, "فرانسه", emptySet()))
        assertEquals(listOf(canada), NoraRouteSelector.searchLocations(all, "CA", emptySet()))
        val britain = NoraNode("gb", "GB", "بریتانیا", "🇬🇧", 45L)
        assertEquals(listOf(britain), NoraRouteSelector.searchLocations(all + britain, "UK", emptySet()))
        assertEquals(listOf(germany), NoraRouteSelector.searchLocations(all, "berlin", emptySet()))
        assertEquals(emptyList<NoraNode>(), NoraRouteSelector.searchLocations(all, "xyz", emptySet()))
    }

    @Test fun favoritesAreFirstAndFilterableWithoutChangingPingRank() {
        val germany = NoraNode("de", "DE", "آلمان", "🇩🇪", 50L)
        val france = NoraNode("fr", "FR", "فرانسه", "🇫🇷", 25L)
        val canada = NoraNode("ca", "CA", "کانادا", "🇨🇦", 70L)
        val all = listOf(france, germany, canada)
        assertEquals(listOf(canada, france, germany),
            NoraRouteSelector.searchLocations(all, "", setOf("CA")))
        assertEquals(listOf(canada),
            NoraRouteSelector.searchLocations(all, "", setOf("CA"), true))
        assertEquals(emptyList<NoraNode>(),
            NoraRouteSelector.searchLocations(all, "Germany", setOf("CA"), true))
    }

    @Test fun unknownLocationsRemainDistinct() {
        assertEquals("UNKNOWN:first", NoraRouteSelector.identify("server alpha", "first").code)
        assertEquals("UNKNOWN:second", NoraRouteSelector.identify("server beta", "second").code)
        assertNotEquals("US", NoraRouteSelector.identify("useless server").code)
    }
}
