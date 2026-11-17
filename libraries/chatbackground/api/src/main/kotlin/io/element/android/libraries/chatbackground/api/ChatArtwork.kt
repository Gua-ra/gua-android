/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/**
 * In gallery order, the same as the assets' manifest.json. iOS ships the same IDs, bubbles, anchors and fallbacks.
 * Whether an artwork is live comes from here, never from the wire.
 */
enum class ChatArtwork(
    val id: String,
    val section: ChatArtworkSection,
    val isLive: Boolean,
    @StringRes val nameRes: Int,
    val bubble: ChatBubbleColor,
    val bubbleDark: ChatBubbleColor,
    /** The point of the image that stays on screen when it is cropped. */
    val anchor: UnitPoint,
    /** What builds without this artwork show instead. It never changes once shipped. */
    val fallbackPreset: ChatBackgroundPreset,
) {
    GlassDrops(
        id = "glass-drops",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_glass_drops,
        bubble = ChatBubbleColor.Purple,
        bubbleDark = ChatBubbleColor.Pink,
        anchor = UnitPoint(0.5f, 0.08f),
        fallbackPreset = ChatBackgroundPreset.Blush,
    ),
    GlassDropsBlue(
        id = "glass-drops-blue",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_glass_drops_blue,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Teal,
        anchor = UnitPoint(0.5f, 0.35f),
        fallbackPreset = ChatBackgroundPreset.Sky,
    ),
    MoltenPearl(
        id = "molten-pearl",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_molten_pearl,
        bubble = ChatBubbleColor.Purple,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Sky,
    ),
    Silk(
        id = "silk",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_silk,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Teal,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    Ipe(
        id = "ipe",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_ipe,
        bubble = ChatBubbleColor.Pink,
        bubbleDark = ChatBubbleColor.Yellow,
        anchor = UnitPoint(0.5f, 0f),
        fallbackPreset = ChatBackgroundPreset.Sunset,
    ),
    RainOnGlass(
        id = "rain-on-glass",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_rain_on_glass,
        bubble = ChatBubbleColor.Green,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.3f),
        fallbackPreset = ChatBackgroundPreset.Aurora,
    ),
    Pantanal(
        id = "pantanal",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_pantanal,
        bubble = ChatBubbleColor.Green,
        bubbleDark = ChatBubbleColor.Green,
        anchor = UnitPoint(0.5f, 0.38f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    CurrentsLive(
        id = "currents-live",
        section = ChatArtworkSection.Live,
        isLive = true,
        nameRes = R.string.gua_chat_theme_art_currents_live,
        bubble = ChatBubbleColor.Purple,
        bubbleDark = ChatBubbleColor.Purple,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Lavender,
    ),
    DuneLagoons(
        id = "dune-lagoons",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_dune_lagoons,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Purple,
        anchor = UnitPoint(0.5f, 0.25f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    PalmCoast(
        id = "palm-coast",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_palm_coast,
        bubble = ChatBubbleColor.Orange,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.2f),
        fallbackPreset = ChatBackgroundPreset.Ocean,
    ),
    RiverBends(
        id = "river-bends",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_river_bends,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Teal,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    HighlandMist(
        id = "highland-mist",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_highland_mist,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Green,
        anchor = UnitPoint(0.5f, 0.3f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    GlacierLake(
        id = "glacier-lake",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_glacier_lake,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Teal,
        anchor = UnitPoint(0.5f, 0.35f),
        fallbackPreset = ChatBackgroundPreset.Ocean,
    ),
    CanolaSky(
        id = "canola-sky",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_canola_sky,
        bubble = ChatBubbleColor.Green,
        bubbleDark = ChatBubbleColor.Orange,
        anchor = UnitPoint(0.4f, 0.38f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    HarbourFog(
        id = "harbour-fog",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_harbour_fog,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.7f, 0.3f),
        fallbackPreset = ChatBackgroundPreset.Ocean,
    ),
    CanyonGlow(
        id = "canyon-glow",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_canyon_glow,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Purple,
        anchor = UnitPoint(0.5f, 0.3f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    PolarIce(
        id = "polar-ice",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_polar_ice,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Purple,
        anchor = UnitPoint(0.5f, 0.3f),
        fallbackPreset = ChatBackgroundPreset.Night,
    ),
    MoonTide(
        id = "moon-tide",
        section = ChatArtworkSection.Nature,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_moon_tide,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.72f),
        fallbackPreset = ChatBackgroundPreset.Ocean,
    ),
    Currents(
        id = "currents",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_currents,
        bubble = ChatBubbleColor.Purple,
        bubbleDark = ChatBubbleColor.Purple,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Lavender,
    ),
    Azulejo(
        id = "azulejo",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_azulejo,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Sky,
    ),
    Calcadao(
        id = "calcadao",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_calcadao,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Teal,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    Knit(
        id = "knit",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_knit,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    DoodlesBrasil(
        id = "doodles-brasil",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_doodles_brasil,
        bubble = ChatBubbleColor.Green,
        bubbleDark = ChatBubbleColor.Green,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Mint,
    ),
    DoodlesCanada(
        id = "doodles-canada",
        section = ChatArtworkSection.Patterns,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_doodles_canada,
        bubble = ChatBubbleColor.Blue,
        bubbleDark = ChatBubbleColor.Blue,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    Paper(
        id = "paper",
        section = ChatArtworkSection.Minimal,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_paper,
        bubble = ChatBubbleColor.Green,
        bubbleDark = ChatBubbleColor.Green,
        anchor = UnitPoint(0.5f, 0.5f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    ),
    PaperCut(
        id = "paper-cut",
        section = ChatArtworkSection.Minimal,
        isLive = false,
        nameRes = R.string.gua_chat_theme_art_paper_cut,
        bubble = ChatBubbleColor.Teal,
        bubbleDark = ChatBubbleColor.Orange,
        anchor = UnitPoint(1f, 0f),
        fallbackPreset = ChatBackgroundPreset.Sand,
    );

    val lightFile: String get() = "$id-light.webp"
    val darkFile: String get() = "$id-dark.webp"

    fun file(isLight: Boolean): String = if (isLight) lightFile else darkFile

    /** The artwork with its paired bubbles, as the gallery offers it. */
    val theme: ChatTheme
        get() = ChatTheme(ChatBackground.Art(this), bubble, bubbleDark).normalized()

    companion object {
        /** The folder of the artworks' images, the grain tile and the manifest in the app's assets. */
        const val ASSETS_FOLDER = "wallpapers"
        const val NOISE_FILE = "noise.webp"

        fun fromId(id: String?): ChatArtwork? = entries.firstOrNull { it.id == id }
    }
}

/** In gallery order; [id] is the manifest's name for the section. */
enum class ChatArtworkSection(val id: String) {
    Live("live"),
    Nature("nature"),
    Patterns("patterns"),
    Minimal("minimal"),
}
