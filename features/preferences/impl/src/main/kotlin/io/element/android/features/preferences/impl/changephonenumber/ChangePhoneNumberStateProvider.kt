/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.R

open class ChangePhoneNumberStateProvider : PreviewParameterProvider<ChangePhoneNumberState> {
    override val values: Sequence<ChangePhoneNumberState>
        get() = sequenceOf(
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Intro),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringNewPhone, phone = "+1"),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringNewPhone,
                phone = "+1abc",
                errorMessage = R.string.screen_change_phone_new_invalid,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringPin, code = "123"),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringPin,
                code = "12",
                errorMessage = R.string.screen_change_phone_pin_incorrect,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringOtp, code = "1234"),
            aChangePhoneNumberState(
                phase = ChangePhoneNumberPhase.EnteringOtp,
                code = "12",
                errorMessage = R.string.screen_change_phone_otp_invalid,
            ),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Submitting, code = "123456"),
            aChangePhoneNumberState(phase = ChangePhoneNumberPhase.Done),
        )
}

fun aChangePhoneNumberState(
    phase: ChangePhoneNumberPhase = ChangePhoneNumberPhase.Intro,
    code: String = "",
    phone: String = "",
    errorMessage: Int? = null,
    eventSink: (ChangePhoneNumberEvents) -> Unit = {},
) = ChangePhoneNumberState(
    phase = phase,
    code = code,
    phone = phone,
    errorMessage = errorMessage,
    eventSink = eventSink,
)
