/*
 * Copyright (c) 2026 Element Creations Ltd.
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
import io.element.android.features.login.impl.screens.phoneentry.country.Country
import io.element.android.libraries.architecture.AsyncData
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK Stage 5 (Localization) verification: records a focused screenshot of the phone-first
 * entry screen rendered with the Brazilian Portuguese (pt-BR) resource qualifier, confirming the
 * Gua-custom strings are translated per device locale (mirroring iOS).
 *
 * Sibling of [GuaPhoneEntryVerifyTest] (which renders the same screen in `en`); only the locale
 * differs, so the screenshot should show the pt-BR copy from
 * `features/login/impl/.../res/values-pt-rBR/gua_translations.xml`
 * (e.g. "Qual é o seu número?" / "Vamos te enviar um código...").
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
                        isLegacyAuthEnabled = true,
                        loginMode = AsyncData.Uninitialized,
                        eventSink = {},
                    ),
                    onOAuthDetails = {},
                    onUseLegacyAuth = {},
                    onSelectCountry = {},
                    onLearnMoreClick = {},
                )
            }
        }
    }
}
