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
 * GUA FORK: localization verification. Records the phone-first entry screen with a Brazilian
 * Portuguese locale, sibling of [GuaPhoneEntryVerifyTest] (the same screen in `en`).
 *
 * Paparazzi resolves the locale by language only, so with both `values-pt` and `values-pt-rBR`
 * present it renders `values-pt`, whichever qualifier form is used here. A device set to pt-BR
 * prefers `values-pt-rBR`. What this test proves is that the screen picks up fork translations at
 * all rather than falling back to English; do not read it as a pt-BR guarantee.
 */
class GuaPhoneEntryPtBrLocaleVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "pt-rBR",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaPhoneEntryScreenPtBr() {
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
