/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.async.AsyncLoading
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.ui.strings.CommonStrings

private val FieldShape = RoundedCornerShape(12.dp)
private val FieldHeight = 56.dp

@Composable
fun ChangePhoneNumberView(
    state: ChangePhoneNumberState,
    onBackClick: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink

    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (state.phase.isEnteringFlow()) {
                eventSink(ChangePhoneNumberEvents.CancelEntry)
            } else {
                onBackClick()
            }
        },
        title = stringResource(id = state.phase.titleRes()),
    ) {
        when (state.phase) {
            ChangePhoneNumberPhase.Intro -> IntroSection(eventSink = eventSink)
            ChangePhoneNumberPhase.EnteringNewPhone -> PhoneEntrySection(state = state, eventSink = eventSink)
            ChangePhoneNumberPhase.EnteringPin,
            ChangePhoneNumberPhase.EnteringOtp -> CodeEntrySection(state = state, eventSink = eventSink)
            ChangePhoneNumberPhase.Submitting -> AsyncLoading()
            ChangePhoneNumberPhase.Done -> DoneSection(eventSink = eventSink, onFinished = onFinished)
        }
    }
}

@Composable
private fun IntroSection(
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Leading icon row, mirroring iOS (userProfile glyph beside the header).
        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CompoundIcons.UserProfileSolid(),
                contentDescription = null,
                tint = ElementTheme.colors.iconPrimary,
            )
            Text(
                text = stringResource(id = R.string.screen_change_phone_intro_header),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = stringResource(id = R.string.screen_change_phone_intro_message),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            onClick = { eventSink(ChangePhoneNumberEvents.Continue) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun PhoneEntrySection(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Header label above the whole row, reading over both the selector and the field.
        Text(
            text = stringResource(id = R.string.screen_change_phone_new_header),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountrySelectorButton(
                country = state.selectedCountry,
                enabled = !state.isWorking,
                onClick = { eventSink(ChangePhoneNumberEvents.SelectCountry) },
            )
            Spacer(modifier = Modifier.width(10.dp))
            PhoneInput(
                value = state.localPhoneNumber,
                onValueChange = { eventSink(ChangePhoneNumberEvents.PhoneChanged(it)) },
                placeholder = state.selectedCountry.nationalExample,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            )
        }
        FooterOrError(
            state = state,
            footerRes = R.string.screen_change_phone_new_footer,
        )
        ContinueButton(state = state, eventSink = eventSink)
    }
}

/**
 * The country pill: flag + "+"+dialCode + chevron in a rounded `bgSubtleSecondary` surface, beside
 * the phone field. Settings-context tokens (NOT the aurora white used on the welcome screen).
 */
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
            .background(ElementTheme.colors.bgSubtleSecondary)
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
            color = ElementTheme.colors.textPrimary,
        )
        Icon(
            imageVector = CompoundIcons.ChevronDown(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

/** A rounded `bgSubtleSecondary` phone field matching the selector pill. */
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
        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
        cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = modifier
            .height(FieldHeight)
            .clip(FieldShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CodeEntrySection(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        PinBubbleField(
            code = state.code,
            length = ChangePhoneNumberState.CODE_LENGTH,
            hasError = state.errorMessage != null,
            enabled = !state.isWorking,
            onValueChange = { eventSink(ChangePhoneNumberEvents.CodeChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
        FooterOrError(
            state = state,
            footerRes = state.phase.footerRes(),
        )
        ContinueButton(state = state, eventSink = eventSink)
    }
}

@Composable
private fun DoneSection(
    eventSink: (ChangePhoneNumberEvents) -> Unit,
    onFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Icon(
            imageVector = CompoundIcons.CheckCircleSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSuccessPrimary,
            modifier = Modifier
                .padding(top = 24.dp)
                .size(40.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_change_phone_done_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_change_phone_done_message),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            text = stringResource(id = CommonStrings.action_done),
            onClick = {
                eventSink(ChangePhoneNumberEvents.Done)
                onFinished()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun FooterOrError(
    state: ChangePhoneNumberState,
    footerRes: Int?,
) {
    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        Text(
            text = stringResource(id = errorMessage),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else if (footerRes != null) {
        Text(
            text = stringResource(id = footerRes),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ContinueButton(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Button(
        text = stringResource(
            id = if (state.isWorking) CommonStrings.common_loading else CommonStrings.action_continue
        ),
        onClick = { eventSink(ChangePhoneNumberEvents.Continue) },
        enabled = state.canContinue,
        showProgress = state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    )
}

/**
 * GUA FORK: 6-bubble code field backed by a single hidden [BasicTextField]. Tapping any bubble focuses
 * the field; each typed digit fills the next bubble. Mirrors the two-step-verification `PinBubbleField`.
 */
@Composable
private fun PinBubbleField(
    code: String,
    length: Int,
    hasError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(length) { index ->
                val digit = code.getOrNull(index)?.toString().orEmpty()
                val borderColor = when {
                    hasError -> ElementTheme.colors.textCriticalPrimary
                    digit.isNotEmpty() -> ElementTheme.colors.iconPrimary
                    else -> ElementTheme.colors.borderInteractivePrimary
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = ElementTheme.colors.bgSubtleSecondary,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit,
                        style = ElementTheme.typography.fontHeadingMdBold,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
            }
        }
        // Invisible input layered over the bubbles to capture the keyboard.
        BasicTextField(
            value = code,
            onValueChange = { onValueChange(it) },
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f),
        )
    }
}

private fun ChangePhoneNumberPhase.isEnteringFlow(): Boolean = when (this) {
    ChangePhoneNumberPhase.EnteringNewPhone,
    ChangePhoneNumberPhase.EnteringPin,
    ChangePhoneNumberPhase.EnteringOtp,
    ChangePhoneNumberPhase.Submitting -> true
    else -> false
}

private fun ChangePhoneNumberPhase.titleRes(): Int = when (this) {
    ChangePhoneNumberPhase.Intro,
    ChangePhoneNumberPhase.Submitting -> R.string.screen_change_phone_title
    ChangePhoneNumberPhase.EnteringNewPhone -> R.string.screen_change_phone_new_header
    ChangePhoneNumberPhase.EnteringPin -> R.string.screen_change_phone_pin_header
    ChangePhoneNumberPhase.EnteringOtp -> R.string.screen_change_phone_otp_header
    ChangePhoneNumberPhase.Done -> R.string.screen_change_phone_done_header
}

private fun ChangePhoneNumberPhase.footerRes(): Int? = when (this) {
    ChangePhoneNumberPhase.EnteringNewPhone -> R.string.screen_change_phone_new_footer
    ChangePhoneNumberPhase.EnteringPin -> R.string.screen_change_phone_pin_footer
    ChangePhoneNumberPhase.EnteringOtp -> R.string.screen_change_phone_otp_footer
    else -> null
}

@PreviewsDayNight
@Composable
internal fun ChangePhoneNumberViewPreview(
    @PreviewParameter(ChangePhoneNumberStateProvider::class) state: ChangePhoneNumberState,
) = ElementPreview {
    ChangePhoneNumberView(
        state = state,
        onBackClick = {},
        onFinished = {},
    )
}
