/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.components.PinBubbleField
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
import io.element.android.libraries.phonenumberentry.PhoneNumberEntryField
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TwoStepVerificationView(
    state: TwoStepVerificationState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPasskeyEnrollUrl: (String) -> Unit = {},
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
    // GUA FORK: once the presenter resolves the authenticated passkey-enrollment URL, open it in a
    // Chrome Custom Tab (the web ceremony), then clear it so re-entering the screen doesn't reopen it.
    val currentOnOpenPasskeyEnrollUrl by rememberUpdatedState(onOpenPasskeyEnrollUrl)
    LaunchedEffect(state.passkeyEnrollUrl) {
        state.passkeyEnrollUrl?.let { url ->
            currentOnOpenPasskeyEnrollUrl(url)
            eventSink(TwoStepVerificationEvent.ClearPasskeyEnrollUrl)
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
            TwoStepVerificationPhase.OverviewNoPin ->
                OverviewSection(hasPin = false, errorMessage = state.errorMessage, eventSink = eventSink)
            TwoStepVerificationPhase.OverviewHasPin ->
                OverviewSection(hasPin = true, errorMessage = state.errorMessage, eventSink = eventSink)
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
    errorMessage: Int?,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    // GUA FORK: one top-level emitter, matching PhoneEntrySection and CodeEntrySection.
    // No modifier: these list rows are deliberately full width.
    Column {
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
        // GUA FORK: passkey setup row, mirroring iOS' "Set up a passkey" row. Opens the authenticated
        // web ceremony (Chrome Custom Tab) where the user registers a passkey at the IdP.
        HorizontalDivider()
        ListItem(
            headlineContent = {
                Text(stringResource(id = R.string.screen_two_step_verification_passkey_button))
            },
            supportingContent = {
                Text(stringResource(id = R.string.screen_two_step_verification_passkey_footer))
            },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
            style = ListItemStyle.Primary,
            onClick = {
                eventSink(TwoStepVerificationEvent.SetUpPasskey)
            },
        )
        // Anything that fails from this screen surfaces here. Without it a failed passkey start wrote
        // an error into the state that no phase on screen rendered, so tapping the row looked like the
        // row did nothing at all.
        if (errorMessage != null) {
            Text(
                text = stringResource(id = errorMessage),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textCriticalPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PhoneEntrySection(
    state: TwoStepVerificationState,
    eventSink: (TwoStepVerificationEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Label above the whole row, reading over both the country selector and the field.
        Text(
            text = stringResource(id = R.string.screen_two_step_verification_phone_label),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
        PhoneNumberEntryField(
            country = state.selectedCountry,
            localPhoneNumber = state.localPhoneNumber,
            onValueChange = { eventSink(TwoStepVerificationEvent.PhoneChanged(it)) },
            onSelectCountry = { eventSink(TwoStepVerificationEvent.SelectCountry) },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth(),
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
            // GUA FORK: mask every secret PIN step (current / new / confirm), but keep the OTP readable.
            masked = state.phase != TwoStepVerificationPhase.EnteringOtp,
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
