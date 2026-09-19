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
import io.element.android.features.login.impl.screens.onboarding.OnBoardingView
import io.element.android.features.login.impl.screens.onboarding.anOnBoardingState
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK verification: records a focused screenshot of the welcome (onboarding) screen to confirm
 * the iOS-parity trust treatment — the welcome title followed by the "End-to-end encrypted" pill
 * (lock icon + label), with NO marketing subtitle below the title.
 *
 * Mirrors the precedent of [GuaPhoneEntryVerifyTest]: records to its own snapshot file and does not
 * touch the shared preview-driven golden set. State uses no custom logo so the title + pill content
 * (not the brand logo) is rendered.
 */
class GuaWelcomeTrustPillVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaWelcomeTrustPill() {
        paparazzi.snapshot {
            ElementTheme {
                OnBoardingView(
                    state = anOnBoardingState(canCreateAccount = true),
                    onBackClick = {},
                    onDeveloperSettingsClick = {},
                    onSignInWithQrCode = {},
                    onSignIn = {},
                    onCreateAccount = {},
                    onReportProblem = {},
                    onOAuthDetails = {},
                    onNeedLoginPassword = {},
                    onLearnMoreClick = {},
                    onCreateAccountContinue = {},
                )
            }
        }
    }
}
