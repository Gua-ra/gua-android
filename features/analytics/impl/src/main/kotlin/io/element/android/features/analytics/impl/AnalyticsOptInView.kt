/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.analytics.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.AnalyticsConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.analytics.api.AnalyticsOptInEvents
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.organisms.InfoListItem
import io.element.android.libraries.designsystem.atomic.organisms.InfoListOrganism
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.ClickableLinkText
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.buildAnnotatedStringWithStyledPart
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AnalyticsOptInView(
    state: AnalyticsOptInState,
    onClickTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink

    fun onAcceptTerms() {
        eventSink(AnalyticsOptInEvents.EnableAnalytics(true))
    }

    fun onDeclineTerms() {
        eventSink(AnalyticsOptInEvents.EnableAnalytics(false))
    }

    BackHandler(onBack = ::onDeclineTerms)
    // GUA FORK: match iOS `AnalyticsPromptScreen`, which renders the header, checklist AND buttons
    // together in the main (white) content area. iOS' gradient is only a thin TOP breaker behind the
    // header; Android's `OnboardingBackground` is a 220dp BOTTOM band, so the dark filled buttons —
    // which naturally fall near the bottom of the content flow — landed ON that teal/blue gradient,
    // giving an ugly dark-on-blue contrast. We therefore (1) move the buttons up into the content
    // (no separate footer over the gradient) and (2) drop the bottom gradient entirely so everything
    // sits on the white `bgCanvasDefault`, matching the iOS result.
    HeaderFooterPage(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
        header = { AnalyticsOptInHeader(state, onClickTerms) },
        content = {
            AnalyticsOptInContent(
                onAcceptTerms = ::onAcceptTerms,
                onDeclineTerms = ::onDeclineTerms,
            )
        },
    )
}

private const val LINK_TAG = "link"

@Composable
private fun AnalyticsOptInHeader(
    state: AnalyticsOptInState,
    onClickTerms: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTitleSubtitleMolecule(
            modifier = Modifier.padding(top = 60.dp, bottom = 28.dp),
            title = stringResource(id = R.string.screen_analytics_prompt_title, state.applicationName),
            subTitle = stringResource(id = R.string.screen_analytics_prompt_help_us_improve),
            iconStyle = BigIcon.Style.Default(CompoundIcons.Chart())
        )
        if (state.hasPolicyLink) {
            val text = buildAnnotatedStringWithStyledPart(
                R.string.screen_analytics_prompt_read_terms,
                R.string.screen_analytics_prompt_read_terms_content_link,
                color = Color.Unspecified,
                underline = false,
                bold = true,
                tagAndLink = LINK_TAG to AnalyticsConfig.POLICY_LINK,
            )
            ClickableLinkText(
                annotatedString = text,
                onClick = { onClickTerms() },
                modifier = Modifier
                    .padding(8.dp),
                style = ElementTheme.typography.fontBodyMdRegular
                    .copy(
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
            )
        }
    }
}

@Composable
private fun AnalyticsOptInContent(
    onAcceptTerms: () -> Unit,
    onDeclineTerms: () -> Unit,
) {
    // GUA FORK: lay the checklist and buttons out as a top-anchored column directly under the
    // header, matching iOS' `VStack(spacing: 40)`. The buttons therefore sit immediately below the
    // checklist in the white content area, NOT at the very bottom over the `OnboardingBackground`
    // gradient (the previous bottom-biased Box pushed them down onto the gradient).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        InfoListOrganism(
            items = persistentListOf(
                InfoListItem(
                    message = stringResource(id = R.string.screen_analytics_prompt_data_usage),
                    iconVector = CompoundIcons.CheckCircle(),
                ),
                InfoListItem(
                    message = stringResource(id = R.string.screen_analytics_prompt_third_party_sharing),
                    iconVector = CompoundIcons.CheckCircle(),
                ),
                InfoListItem(
                    message = stringResource(id = R.string.screen_analytics_prompt_settings),
                    iconVector = CompoundIcons.CheckCircle(),
                ),
            ),
            textStyle = ElementTheme.typography.fontBodyLgMedium,
            iconTint = ElementTheme.colors.iconSuccessPrimary,
        )
        // GUA FORK: the OK / Not now buttons live here in the white content area (matching iOS,
        // where they sit in `mainContent` rather than over the bottom gradient) so the dark
        // filled buttons stay high-contrast against `bgCanvasDefault`.
        AnalyticsOptInButtons(
            onAcceptTerms = onAcceptTerms,
            onDeclineTerms = onDeclineTerms,
        )
    }
}

@Composable
private fun AnalyticsOptInButtons(
    onAcceptTerms: () -> Unit,
    onDeclineTerms: () -> Unit,
) {
    ButtonColumnMolecule {
        Button(
            text = stringResource(id = CommonStrings.action_ok),
            onClick = onAcceptTerms,
            modifier = Modifier.fillMaxWidth(),
        )
        // GUA FORK: both choices are equal filled primary buttons (matching iOS, which uses
        // `.compound(.primary)` on both) so neither option is visually nudged.
        Button(
            text = stringResource(id = CommonStrings.action_not_now),
            onClick = onDeclineTerms,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun AnalyticsOptInViewPreview(@PreviewParameter(AnalyticsOptInStateProvider::class) state: AnalyticsOptInState) = ElementPreview {
    AnalyticsOptInView(
        state = state,
        onClickTerms = {},
    )
}
