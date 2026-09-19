/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.phonenumberentry.Country

private val US = Country(isoCode = "US", dialCode = "1")
private val BR = Country(isoCode = "BR", dialCode = "55")

open class ChangePhoneNumberStateProvider : PreviewParameterProvider<ChangePhoneNumberState> {
    override val values: Sequence<ChangePhoneNumberState>
        get() = sequenceOf(
            // GUA FORK: exactly as many entries as before. Paparazzi shards @PreviewsDayNight by
            // index, so adding or removing one re-shards the whole list and churns dozens of
            // unrelated goldens; states that only matter to the state machine are covered by
            // ChangePhoneNumberPresenterTest instead.
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Intro),
            // A spent reauth token drops the user back on the intro, with the reason.
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.Intro,
                errorMessage = R.string.screen_change_phone_pin_incorrect,
            ),
            // The hard block, in both of its shapes: no factor at all (passkey or PIN), and a
            // passkey this build cannot assert (PIN only, since a second passkey cannot be enrolled).
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.NeedsStepUp,
                stepUpBlock = StepUpBlock.NoFactorRegistered,
            ),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.NeedsStepUp,
                stepUpBlock = StepUpBlock.PasskeyNotUsableHere,
            ),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.Cooldown,
                // 6 days, 3 hours -> exercises the multi-unit humaniser.
                cooldownRemainingSeconds = 6L * 24 * 3600 + 3 * 3600,
            ),
            // The OTP to the number already on file, then the step-up factor (masked).
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringReauthOtp, code = "123"),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringPin, code = "123"),
            // New-number step: empty (shows the placeholder) and a filled US number.
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringNewPhone),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = US,
                localPhoneNumber = "5551234567",
            ),
            // New-number step with a different country selected (flag + dial code change).
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = BR,
                localPhoneNumber = "11912345678",
            ),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                selectedCountry = US,
                localPhoneNumber = "5",
                errorMessage = R.string.screen_change_phone_new_invalid,
            ),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringOtp,
                code = "12",
                errorMessage = R.string.screen_change_phone_otp_invalid,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Done),
        )
}

fun aChangePhoneNumberState(
    phase: ChangePhoneNumberPhase = ChangePhoneNumberPhase.Intro,
    code: String = "",
    selectedCountry: Country = US,
    localPhoneNumber: String = "",
    errorMessage: Int? = null,
    cooldownRemainingSeconds: Long = 0,
    stepUpBlock: StepUpBlock? = null,
    passkeyEnrollUrl: String? = null,
    eventSink: (ChangePhoneNumberEvents) -> Unit = {},
) = ChangePhoneNumberState(
    phase = phase,
    code = code,
    selectedCountry = selectedCountry,
    localPhoneNumber = localPhoneNumber,
    errorMessage = errorMessage,
    cooldownRemainingSeconds = cooldownRemainingSeconds,
    stepUpBlock = stepUpBlock,
    passkeyEnrollUrl = passkeyEnrollUrl,
    eventSink = eventSink,
)
