/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Gua brand green, mirroring the maned-wolf app-icon mark and Element X iOS' inherited accent.
 *
 * The icon carries two greens: a vivid mark green ([GUA_GREEN_BRIGHT], ~#00CC8A) and a deep
 * shade ([GUA_GREEN_DEEP], ~#006042). We map them onto the Compound accent tokens following the
 * design system's own light/dark contrast logic — the deep green for accents that sit on light
 * (white) surfaces, the bright green for accents on dark surfaces — so contrast stays comparable
 * to the stock Element green ramp.
 */
internal val GUA_GREEN_BRIGHT = Color(0xFF00CC8A)
internal val GUA_GREEN_DEEP = Color(0xFF006042)
private val GUA_GREEN_BRIGHT_PRESSED = Color(0xFF00A872)
private val GUA_GREEN_DEEP_PRESSED = Color(0xFF004D35)
private val GUA_GREEN_SUBTLE_LIGHT = Color(0xFF0BC491)
private val GUA_GREEN_SUBTLE_DARK = Color(0xFF1FC090)

/**
 * Replace the green accent tokens of [base] with the Gua brand green.
 * Only accent/brand tokens are touched; everything else (canvas, text, critical, etc.) is left
 * as the Compound default — matching iOS, which keeps the dark/gray primary action button.
 */
private fun SemanticColors.withGuaAccent(): SemanticColors {
    val primary = if (isLight) GUA_GREEN_DEEP else GUA_GREEN_BRIGHT
    val pressed = if (isLight) GUA_GREEN_DEEP_PRESSED else GUA_GREEN_BRIGHT_PRESSED
    val subtle = if (isLight) GUA_GREEN_SUBTLE_LIGHT else GUA_GREEN_SUBTLE_DARK
    return copy(
        bgAccentRest = primary,
        bgAccentHovered = pressed,
        bgAccentPressed = pressed,
        bgBadgeAccent = subtle,
        borderAccentPrimary = primary,
        borderAccentSubtle = subtle,
        iconAccentPrimary = primary,
        iconAccentTertiary = primary,
        textActionAccent = primary,
        textBadgeAccent = primary,
    )
}

private val guaSemanticColors: SemanticColorsLightDark by lazy {
    SemanticColorsLightDark(
        light = SemanticColorsLightDark.default.light.withGuaAccent(),
        dark = SemanticColorsLightDark.default.dark.withGuaAccent(),
    )
}

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService : EnterpriseService {
    override val isEnterpriseBuild = false

    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, homeserver: String) = url
    override fun defaultHomeserverList(): List<String> = emptyList()
    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String) = true

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(GUA_GREEN_BRIGHT)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(guaSemanticColors)
    }

    override fun firebasePushGateway(): String? = null
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.UseDefault)
    }

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null
}
