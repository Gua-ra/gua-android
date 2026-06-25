/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.async.AsyncLoading
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TwoStepVerificationView(
    state: TwoStepVerificationState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(id = R.string.screen_two_step_verification_updated)
    LaunchedEffect(state.showSuccess) {
        if (state.showSuccess) {
            snackbarHostState.showSnackbar(successMessage)
            eventSink(TwoStepVerificationEvent.ClearSuccess)
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (state.phase.isEnteringFlow()) {
                eventSink(TwoStepVerificationEvent.CancelEntry)
            } else {
                onBackClick()
            }
        },
        title = stringResource(id = state.phase.titleRes()),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        when (state.phase) {
            TwoStepVerificationPhase.Loading -> {
                AsyncLoading()
            }
            TwoStepVerificationPhase.OverviewNoPin -> OverviewSection(hasPin = false, eventSink = eventSink)
            TwoStepVerificationPhase.OverviewHasPin -> OverviewSection(hasPin = true, eventSink = eventSink)
            TwoStepVerificationPhase.EnteringPhone -> PhoneEntrySection(state = state, eventSink = eventSink)
            TwoStepVerificationPhase.EnteringCurrent,
            TwoStepVerificationPhase.EnteringOtp,
            TwoStepVerificationPhase.EnteringNew,
            TwoStepVerificationPhase.ConfirmingNew,
            TwoStepVerificationPhase.Submitting -> CodeEntrySection(state = state, eventSink = eventSink)
        }
    }
}

@Composable
private fun OverviewSection(
    hasPin: Boolean,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    id = if (hasPin) {
                        R.string.screen_two_step_verification_status_on
                    } else {
                        R.string.screen_two_step_verification_status_off
                    }
                )
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    id = if (hasPin) {
                        R.string.screen_two_step_verification_overview_footer_on
                    } else {
                        R.string.screen_two_step_verification_overview_footer_off
                    }
                )
            )
        },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
    )
    HorizontalDivider()
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    id = if (hasPin) {
                        R.string.screen_two_step_verification_change_button
                    } else {
                        R.string.screen_two_step_verification_set_button
                    }
                )
            )
        },
        leadingContent = ListItemContent.Icon(
            IconSource.Vector(if (hasPin) CompoundIcons.Edit() else CompoundIcons.Lock())
        ),
        style = ListItemStyle.Primary,
        onClick = {
            eventSink(if (hasPin) TwoStepVerificationEvent.StartChange else TwoStepVerificationEvent.StartSetup)
        },
    )
}

@Composable
private fun PhoneEntrySection(
    state: TwoStepVerificationState,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextField(
            value = state.phone,
            onValueChange = { eventSink(TwoStepVerificationEvent.PhoneChanged(it)) },
            label = stringResource(id = R.string.screen_two_step_verification_phone_label),
            placeholder = "+1",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )
        FooterOrError(
            state = state,
            footerRes = R.string.screen_two_step_verification_phone_footer,
        )
        ContinueButton(state = state, eventSink = eventSink)
    }
}

@Composable
private fun CodeEntrySection(
    state: TwoStepVerificationState,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        PinBubbleField(
            code = state.code,
            length = TwoStepVerificationState.CODE_LENGTH,
            hasError = state.errorMessage != null,
            enabled = !state.isWorking,
            onValueChange = { eventSink(TwoStepVerificationEvent.CodeChanged(it)) },
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
private fun FooterOrError(
    state: TwoStepVerificationState,
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
    state: TwoStepVerificationState,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    Button(
        text = stringResource(
            id = if (state.isWorking) CommonStrings.common_loading else CommonStrings.action_continue
        ),
        onClick = { eventSink(TwoStepVerificationEvent.Continue) },
        enabled = state.canContinue,
        showProgress = state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    )
}

/**
 * GUA FORK: 6-bubble code field backed by a single hidden [BasicTextField]. Tapping any bubble focuses
 * the field; each typed digit fills the next bubble. Android counterpart of iOS `PinBubbleField`.
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

private fun TwoStepVerificationPhase.isEnteringFlow(): Boolean = when (this) {
    TwoStepVerificationPhase.EnteringPhone,
    TwoStepVerificationPhase.EnteringCurrent,
    TwoStepVerificationPhase.EnteringOtp,
    TwoStepVerificationPhase.EnteringNew,
    TwoStepVerificationPhase.ConfirmingNew,
    TwoStepVerificationPhase.Submitting -> true
    else -> false
}

private fun TwoStepVerificationPhase.titleRes(): Int = when (this) {
    TwoStepVerificationPhase.Loading,
    TwoStepVerificationPhase.OverviewNoPin,
    TwoStepVerificationPhase.OverviewHasPin,
    TwoStepVerificationPhase.Submitting -> R.string.screen_two_step_verification_title
    TwoStepVerificationPhase.EnteringPhone -> R.string.screen_two_step_verification_phone_header
    TwoStepVerificationPhase.EnteringCurrent -> R.string.screen_two_step_verification_current_header
    TwoStepVerificationPhase.EnteringOtp -> R.string.screen_two_step_verification_otp_header
    TwoStepVerificationPhase.EnteringNew -> R.string.screen_two_step_verification_new_header
    TwoStepVerificationPhase.ConfirmingNew -> R.string.screen_two_step_verification_confirm_header
}

private fun TwoStepVerificationPhase.footerRes(): Int? = when (this) {
    TwoStepVerificationPhase.EnteringPhone -> R.string.screen_two_step_verification_phone_footer
    TwoStepVerificationPhase.EnteringCurrent -> R.string.screen_two_step_verification_current_footer
    TwoStepVerificationPhase.EnteringOtp -> R.string.screen_two_step_verification_otp_footer
    TwoStepVerificationPhase.EnteringNew -> R.string.screen_two_step_verification_new_footer
    TwoStepVerificationPhase.ConfirmingNew -> R.string.screen_two_step_verification_confirm_footer
    else -> null
}

@PreviewsDayNight
@Composable
internal fun TwoStepVerificationViewPreview(
    @PreviewParameter(TwoStepVerificationStateProvider::class) state: TwoStepVerificationState,
) = ElementPreview {
    TwoStepVerificationView(
        state = state,
        onBackClick = {},
    )
}
