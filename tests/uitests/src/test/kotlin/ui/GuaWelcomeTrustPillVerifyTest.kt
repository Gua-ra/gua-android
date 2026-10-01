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
