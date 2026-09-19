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
 * GUA FORK Stage 5 (Localization) verification: records a focused screenshot of the phone-first
 * entry screen rendered with the Brazilian Portuguese (pt-BR) resource qualifier, confirming the
 * Gua-custom strings are translated per device locale (mirroring iOS).
 *
 * Sibling of [GuaPhoneEntryVerifyTest] (which renders the same screen in `en`); only the locale
 * differs.
 *
 * KNOWN LIMITATION, do not read this as a pt-BR guarantee. Paparazzi resolves the locale by
 * LANGUAGE only: with both `values-pt` and `values-pt-rBR` present it renders `values-pt`, the
 * European Portuguese copy, whichever qualifier form is used here (`pt-BR` is rejected outright,
 * `b+pt+BR` resolves the same as `pt-rBR`). Before `values-pt` existed this happened to render the
 * Brazilian strings, which is why the recorded image changed when the fork was translated.
 *
 * A real device set to pt-BR is unaffected: Android prefers `values-pt-rBR`, and both variants ship
 * in the APK (`aapt2 dump resources` shows `(pt) "Introduza o seu número de telemóvel"` alongside
 * `(pt-rBR) "Digite seu número de telefone"`). What this test still covers is that the screen picks
 * up fork translations at all rather than falling back to English.
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
