/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryState
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryView
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.phonenumberentry.Country
import org.junit.Rule
import org.junit.Test

/**
 * Records a focused screenshot of the phone-first entry screen with a typed, valid Brazilian number:
 * the country selector, the masked phone field, and no homeserver or Matrix copy anywhere on the
 * entry surface. Records to its own snapshot file.
 */
class GuaPhoneEntryVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaPhoneEntryScreen() {
        paparazzi.snapshot {
            ElementTheme {
                PhoneEntryView(
                    state = PhoneEntryState(
                        selectedCountry = Country(isoCode = "BR", dialCode = "55"),
                        localPhoneNumber = "(11) 91234-5678",
                        loginMode = AsyncData.Uninitialized,
                        eventSink = {},
                    ),
                    onOAuthDetails = {},
                    onSelectCountry = {},
                    onLearnMoreClick = {},
                )
            }
        }
    }
}
