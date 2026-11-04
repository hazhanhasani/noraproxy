package com.v2ray.ang.ui.main

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Nora's independent visual identity. The upstream ThemeManager remains the only
 * persistent setting and system-bars owner; Nora simply renders its own palette.
 */
internal enum class NoraThemeMode(val storageValue: String, val label: String) {
    System("0", "سیستم"),
    Light("1", "روشن"),
    Dark("2", "تاریک");

    companion object {
        fun fromStorage(value: String?): NoraThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

internal data class NoraPalette(
    val ink: Color,
    val surface: Color,
    val surface2: Color,
    val cyan: Color,
    val blue: Color,
    val light: Color,
    val muted: Color,
    val green: Color,
    val amber: Color,
    val edge: Color,
    val gradientMid: Color,
    val homeGradientMid: Color,
    val warningBackground: Color,
    val warningText: Color,
    val error: Color,
    val dark: Boolean
) {
    fun materialColors(): ColorScheme = if (dark) {
        darkColorScheme(
            primary = cyan,
            onPrimary = Color.White,
            secondary = blue,
            onSecondary = Color.White,
            background = ink,
            onBackground = light,
            surface = surface,
            onSurface = light,
            surfaceVariant = surface2,
            onSurfaceVariant = muted,
            outline = edge,
            error = error
        )
    } else {
        lightColorScheme(
            primary = cyan,
            onPrimary = Color.White,
            secondary = blue,
            onSecondary = Color.White,
            background = ink,
            onBackground = light,
            surface = surface,
            onSurface = light,
            surfaceVariant = surface2,
            onSurfaceVariant = muted,
            outline = edge,
            error = error
        )
    }
}

internal object NoraPalettes {
    val Dark = NoraPalette(
        ink = Color(0xFF08090E),
        surface = Color(0xFF121319),
        surface2 = Color(0xFF181A22),
        cyan = Color(0xFF4B7DFF),
        blue = Color(0xFF725BFF),
        light = Color(0xFFF6F7FB),
        muted = Color(0xFFB9BCC7),
        green = Color(0xFF2ED3A1),
        amber = Color(0xFFFFB454),
        edge = Color(0xFF2A2D38),
        gradientMid = Color(0xFF12192B),
        homeGradientMid = Color(0xFF0E1020),
        warningBackground = Color(0xFF30281C),
        warningText = Color(0xFFFFD18A),
        error = Color(0xFFFFB5A9),
        dark = true
    )
    val Light = NoraPalette(
        ink = Color(0xFFF5F7FB),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFEDF2F9),
        cyan = Color(0xFF285BC6),
        blue = Color(0xFF5540B7),
        light = Color(0xFF152239),
        muted = Color(0xFF53637B),
        green = Color(0xFF146C4E),
        amber = Color(0xFF955A00),
        edge = Color(0xFFD7E0EC),
        gradientMid = Color(0xFFE7EFFB),
        homeGradientMid = Color(0xFFEDF3FF),
        warningBackground = Color(0xFFFFF2DD),
        warningText = Color(0xFF825000),
        error = Color(0xFFB42318),
        dark = false
    )

    fun resolve(mode: NoraThemeMode, systemDark: Boolean): NoraPalette = when (mode) {
        NoraThemeMode.Dark -> Dark
        NoraThemeMode.Light -> Light
        NoraThemeMode.System -> if (systemDark) Dark else Light
    }
}

internal val LocalNoraPalette = compositionLocalOf { NoraPalettes.Dark }
