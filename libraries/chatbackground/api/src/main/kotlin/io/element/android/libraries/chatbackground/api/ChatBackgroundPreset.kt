/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/** In picker order. iOS ships the same IDs and colours, all darker than the bubbles of their appearance. */
enum class ChatBackgroundPreset(
    val id: String,
    @StringRes val nameRes: Int,
    val light: ChatBackgroundFill,
    val dark: ChatBackgroundFill,
) {
    Aurora(
        id = "aurora",
        nameRes = R.string.gua_chat_background_preset_aurora,
        light = ChatBackgroundFill.Aurora(base = 0xFFB2D8C2, glowA = 0xFF8FCDAB, glowB = 0xFF93CBCF),
        dark = ChatBackgroundFill.Aurora(base = 0xFF030D08, glowA = 0xFF061B11, glowB = 0xFF05181A),
    ),
    Mint(
        id = "mint",
        nameRes = R.string.gua_chat_background_preset_mint,
        light = ChatBackgroundFill.Solid(0xFFADD7BF),
        dark = ChatBackgroundFill.Solid(0xFF08190F),
    ),
    Sky(
        id = "sky",
        nameRes = R.string.gua_chat_background_preset_sky,
        light = ChatBackgroundFill.Solid(0xFFB6D1EB),
        dark = ChatBackgroundFill.Solid(0xFF0A1524),
    ),
    Sand(
        id = "sand",
        nameRes = R.string.gua_chat_background_preset_sand,
        light = ChatBackgroundFill.Solid(0xFFDCC9A9),
        dark = ChatBackgroundFill.Solid(0xFF19130B),
    ),
    Blush(
        id = "blush",
        nameRes = R.string.gua_chat_background_preset_blush,
        light = ChatBackgroundFill.Solid(0xFFE8C3CA),
        dark = ChatBackgroundFill.Solid(0xFF200F17),
    ),
    Lavender(
        id = "lavender",
        nameRes = R.string.gua_chat_background_preset_lavender,
        light = ChatBackgroundFill.Solid(0xFFD0C6EB),
        dark = ChatBackgroundFill.Solid(0xFF151125),
    ),
    Graphite(
        id = "graphite",
        nameRes = R.string.gua_chat_background_preset_graphite,
        light = ChatBackgroundFill.Solid(0xFFC5CAD3),
        dark = ChatBackgroundFill.Solid(0xFF000000),
    ),
    Sunset(
        id = "sunset",
        nameRes = R.string.gua_chat_background_preset_sunset,
        light = ChatBackgroundFill.LinearGradient.vertical(0xFFF0C6A6 at 0f, 0xFFEAB5C2 at 0.5f, 0xFFCBB5E4 at 1f),
        dark = ChatBackgroundFill.LinearGradient.vertical(0xFF22100C at 0f, 0xFF220D1C at 0.5f, 0xFF161029 at 1f),
    ),
    Ocean(
        id = "ocean",
        nameRes = R.string.gua_chat_background_preset_ocean,
        light = ChatBackgroundFill.LinearGradient.diagonal(0xFFA4D5D9 at 0f, 0xFFA7C6EB at 1f),
        dark = ChatBackgroundFill.LinearGradient.diagonal(0xFF05181A at 0f, 0xFF09132A at 1f),
    ),
    Night(
        id = "night",
        nameRes = R.string.gua_chat_background_preset_night,
        light = ChatBackgroundFill.LinearGradient.vertical(0xFFC1C6EC at 0f, 0xFFD4C0E6 at 1f),
        dark = ChatBackgroundFill.LinearGradient.vertical(0xFF0A0D20 at 0f, 0xFF170C22 at 1f),
    );

    fun fill(isLight: Boolean): ChatBackgroundFill = if (isLight) light else dark

    companion object {
        private val ID_FORMAT = Regex("^[a-z0-9_]{1,32}$")

        fun fromId(id: String?): ChatBackgroundPreset? {
            if (id == null || !ID_FORMAT.matches(id)) return null
            return entries.firstOrNull { it.id == id }
        }
    }
}

/**
 * How a preset paints its frame. Colours are sRGB ARGB values; points are in unit coordinates of the frame, x from the left and y from the top,
 * never mirrored for right-to-left layouts.
 */
sealed interface ChatBackgroundFill {
    data class Solid(val color: Long) : ChatBackgroundFill

    data class LinearGradient(val start: UnitPoint, val end: UnitPoint, val stops: List<GradientStop>) : ChatBackgroundFill {
        companion object {
            fun vertical(vararg stops: GradientStop) = LinearGradient(UnitPoint(0.5f, 0f), UnitPoint(0.5f, 1f), stops.toList())
            fun diagonal(vararg stops: GradientStop) = LinearGradient(UnitPoint(0f, 0f), UnitPoint(1f, 1f), stops.toList())
        }
    }

    /** Two radial glows over [base], each fading to transparent; a radius is a fraction of the frame's longest side. */
    data class Aurora(val base: Long, val glowA: Long, val glowB: Long) : ChatBackgroundFill {
        val glowACenter get() = UnitPoint(0.30f, 0.25f)
        val glowARadius get() = 0.75f
        val glowBCenter get() = UnitPoint(0.75f, 0.80f)
        val glowBRadius get() = 0.65f
    }
}

data class UnitPoint(val x: Float, val y: Float)

data class GradientStop(val color: Long, val offset: Float)

private infix fun Long.at(offset: Float) = GradientStop(this, offset)
