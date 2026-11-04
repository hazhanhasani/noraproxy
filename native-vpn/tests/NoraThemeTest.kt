package com.v2ray.ang.ui.main

import org.junit.Assert.*
import org.junit.Test

class NoraThemeTest {
    @Test fun resolvesThemeValuesWithSafeSystemFallback() {
        assertEquals(NoraThemeMode.System, NoraThemeMode.fromStorage(null))
        assertEquals(NoraThemeMode.System, NoraThemeMode.fromStorage("invalid"))
        assertEquals(NoraThemeMode.Light, NoraThemeMode.fromStorage("1"))
        assertEquals(NoraThemeMode.Dark, NoraThemeMode.fromStorage("2"))
        assertEquals(NoraPalettes.Dark, NoraPalettes.resolve(NoraThemeMode.System, true))
        assertEquals(NoraPalettes.Light, NoraPalettes.resolve(NoraThemeMode.System, false))
        assertEquals(NoraPalettes.Dark, NoraPalettes.resolve(NoraThemeMode.Dark, false))
        assertEquals(NoraPalettes.Light, NoraPalettes.resolve(NoraThemeMode.Light, true))
    }

    @Test fun darkAndLightSchemesRemainDistinctAndAccessible() {
        val dark = NoraPalettes.Dark
        val light = NoraPalettes.Light
        assertNotEquals(dark.ink, light.ink)
        assertNotEquals(dark.light, light.light)
        assertEquals(dark.light, dark.materialColors().onSurface)
        assertEquals(light.light, light.materialColors().onSurface)
        assertEquals(dark.cyan, dark.materialColors().primary)
        assertEquals(light.cyan, light.materialColors().primary)
    }
}
