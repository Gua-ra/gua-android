/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.login.LoginModeView
import io.element.android.features.login.impl.screens.phoneentry.country.Country
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun PhoneEntryView(
    state: PhoneEntryState,
    onOAuthDetails: (OAuthDetails) -> Unit,
    onSelectCountry: () -> Unit,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading by remember(state.loginMode) {
        derivedStateOf { state.loginMode is AsyncData.Loading }
    }
    val eventSink = state.eventSink

    HeaderFooterPage(
        modifier = modifier,
        header = {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 60.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.Chat()),
                title = stringResource(R.string.screen_phone_entry_title),
                subTitle = stringResource(R.string.screen_phone_entry_message),
            )
        },
        footer = {
            ButtonColumnMolecule {
                Button(
                    text = stringResource(id = CommonStrings.action_continue),
                    showProgress = state.isSubmitting,
                    onClick = { eventSink(PhoneEntryEvents.Continue) },
                    enabled = state.canContinue || isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.loginContinue),
                )
            }
        },
    ) {
        PhoneNumberField(
            state = state,
            enabled = !isLoading,
            onSelectCountry = onSelectCountry,
            onValueChange = { eventSink(PhoneEntryEvents.PhoneNumberChanged(it)) },
        )
        LoginModeView(
            loginMode = state.loginMode,
            onClearError = { eventSink(PhoneEntryEvents.ClearError) },
            onLearnMoreClick = onLearnMoreClick,
            onOAuthDetails = onOAuthDetails,
            // Phone-first entry never produces password/account-creation modes (MAS OIDC only).
            onNeedLoginPassword = {},
            onCreateAccountContinue = {},
        )
    }
}

@Composable
private fun PhoneNumberField(
    state: PhoneEntryState,
    enabled: Boolean,
    onSelectCountry: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountrySelectorButton(
            country = state.selectedCountry,
            enabled = enabled,
            onClick = onSelectCountry,
        )
        Spacer(modifier = Modifier.width(12.dp))
        TextField(
            value = state.localPhoneNumber,
            onValueChange = onValueChange,
            label = stringResource(R.string.screen_phone_entry_phone_number_label),
            placeholder = state.selectedCountry.nationalExample,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .weight(1f)
                .testTag(TestTags.loginEmailUsername),
        )
    }
}

@Composable
private fun CountrySelectorButton(
    country: Country,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.flag,
            style = ElementTheme.typography.fontHeadingMdBold,
        )
        Text(
            text = "+" + country.dialCode,
            modifier = Modifier.padding(start = 8.dp),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun PhoneEntryViewPreview(
    @PreviewParameter(PhoneEntryStateProvider::class) state: PhoneEntryState,
) = ElementPreview {
    PhoneEntryView(
        state = state,
        onOAuthDetails = {},
        onSelectCountry = {},
        onLearnMoreClick = {},
    )
}
