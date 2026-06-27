/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.login.LoginModeView
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.atoms.GuaWelcomeLogo
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.background.GuaWelcomeBackground
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

// The aurora canvas is dark in both themes, so the welcome uses light, brand-fixed colours rather
// than theme tokens (which would be near-black in light mode and vanish against the green).
private val OnAuroraPrimary = Color.White
private val OnAuroraSecondary = Color.White.copy(alpha = 0.78f)
private val OnAuroraHint = Color.White.copy(alpha = 0.5f)
private val FieldFill = Color.White.copy(alpha = 0.12f)
private val FieldStroke = Color.White.copy(alpha = 0.22f)
private val FieldShape = RoundedCornerShape(12.dp)
private val FieldHeight = 56.dp

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
        // Paint the branded Gua aurora behind everything, and let it show through the page.
        background = { GuaWelcomeBackground() },
        containerColor = Color.Transparent,
        header = { WelcomeHeader() },
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
            // Phone-first entry never produces password/account-creation modes (MAS OIDC only).
            onNeedLoginPassword = {},
            onCreateAccountContinue = {},
            onOAuthDetails = onOAuthDetails,
        )
    }
}

/**
 * The branded welcome header: the clean [GuaWelcomeLogo] above the title + message, in light text
 * over the dark [GuaWelcomeBackground] aurora.
 */
@Composable
private fun WelcomeHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        GuaWelcomeLogo()
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.screen_phone_entry_welcome_title),
            color = OnAuroraPrimary,
            style = ElementTheme.typography.fontHeadingLgBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.screen_phone_entry_message),
            color = OnAuroraSecondary,
            style = ElementTheme.typography.fontBodyLgRegular,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
    ) {
        // Single label above the whole row, so it reads over BOTH the country code and the field.
        Text(
            text = stringResource(R.string.screen_phone_entry_phone_number_label),
            color = OnAuroraSecondary,
            style = ElementTheme.typography.fontBodyMdMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountrySelectorButton(
                country = state.selectedCountry,
                enabled = enabled,
                onClick = onSelectCountry,
            )
            Spacer(modifier = Modifier.width(10.dp))
            PhoneInput(
                value = state.localPhoneNumber,
                onValueChange = onValueChange,
                placeholder = state.selectedCountry.nationalExample,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.loginEmailUsername),
            )
        }
        // Footer under the field, mirroring iOS ("We'll text a verification code to this number.").
        Text(
            text = stringResource(R.string.screen_phone_entry_footer),
            color = OnAuroraSecondary,
            style = ElementTheme.typography.fontBodySmRegular,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

/** A translucent "glass" phone input that reads on the dark aurora — never a white-on-green box. */
@Composable
private fun PhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = OnAuroraPrimary),
        cursorBrush = SolidColor(OnAuroraPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = modifier
            .height(FieldHeight)
            .clip(FieldShape)
            .background(FieldFill)
            .border(1.dp, FieldStroke, FieldShape)
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = OnAuroraHint,
                    )
                }
                innerTextField()
            }
        },
    )
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
            .height(FieldHeight)
            .clip(FieldShape)
            .background(FieldFill)
            .border(1.dp, FieldStroke, FieldShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
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
            color = OnAuroraPrimary,
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
