/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.changephonenumber.ChangePhoneNumberPhase
import io.element.android.features.preferences.impl.changephonenumber.ChangePhoneNumberView
import io.element.android.features.preferences.impl.changephonenumber.aChangePhoneNumberState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.phonenumberentry.Country
import org.junit.Rule
import org.junit.Test

/**
 * Records the step that asks the signed-in user which number is on their account.
 *
 * The third shot is the refusal. The server answers "unknown number", "someone else's number" and
 * "not this account's number" identically, so the recorded wording is the one neutral sentence.
 *
 * Records to its own snapshot files and adds no preview.
 */
class GuaChangePhoneCurrentNumberVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaChangePhoneCurrentNumberEmpty() {
        snapshotCurrentNumberStep(localPhoneNumber = "")
    }

    @Test
    fun guaChangePhoneCurrentNumberFilled() {
        snapshotCurrentNumberStep(localPhoneNumber = "5559876543")
    }

    @Test
    fun guaChangePhoneCurrentNumberMismatch() {
        snapshotCurrentNumberStep(
            localPhoneNumber = "5550000000",
            errorMessage = R.string.screen_change_phone_current_mismatch,
        )
    }

    private fun snapshotCurrentNumberStep(localPhoneNumber: String, errorMessage: Int? = null) {
        paparazzi.snapshot {
            ElementPreview {
                ChangePhoneNumberView(
                    state = aChangePhoneNumberState(
                        phase = ChangePhoneNumberPhase.EnteringCurrentPhone,
                        selectedCountry = Country(isoCode = "US", dialCode = "1"),
                        localPhoneNumber = localPhoneNumber,
                        errorMessage = errorMessage,
                    ),
                    onBackClick = {},
                    onFinish = {},
                    onOpenPasskeyEnrollUrl = {},
                )
            }
        }
    }
}
