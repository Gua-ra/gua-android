/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.features.preferences.impl.changephonenumber.ChangePhoneNumberPhase
import io.element.android.features.preferences.impl.changephonenumber.ChangePhoneNumberView
import io.element.android.features.preferences.impl.changephonenumber.aChangePhoneNumberState
import io.element.android.features.preferences.impl.twostepverification.TwoStepVerificationView
import io.element.android.features.preferences.impl.twostepverification.aTwoStepVerificationState
import io.element.android.features.securebackup.impl.reset.root.ResetIdentityRootState
import io.element.android.features.securebackup.impl.reset.root.ResetIdentityRootView
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AuthFactor
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: the two-step verification, change number and identity reset screens in Spanish and
 * French, so English left on them shows up in the image. Standalone snapshots, so the preview
 * goldens are not re-sharded. pt-BR is left out because Paparazzi renders values-pt for it.
 */
abstract class GuaSecurityScreensLocaleTest(locale: String) {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = locale,
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaTwoStepVerificationPasskeyOn() {
        paparazzi.snapshot {
            ElementPreview {
                TwoStepVerificationView(
                    state = aTwoStepVerificationState(
                        factors = AccountFactorStatus(
                            hasPin = false,
                            passkeyRegistered = true,
                            preferredFactor = AuthFactor.PASSKEY,
                            phoneChangeStepUpFactors = listOf(AuthFactor.PASSKEY, AuthFactor.PIN),
                            changePhoneCooldownRemainingSeconds = 0,
                        ),
                    ),
                    onBackClick = {},
                )
            }
        }
    }

    @Test
    fun guaChangePhoneReauth() {
        paparazzi.snapshot {
            ElementPreview {
                ChangePhoneNumberView(
                    state = aChangePhoneNumberState(phase = ChangePhoneNumberPhase.EnteringReauthOtp, code = "12"),
                    onBackClick = {},
                    onFinish = {},
                    onOpenPasskeyEnrollUrl = {},
                )
            }
        }
    }

    @Test
    fun guaResetIdentityRoot() {
        paparazzi.snapshot {
            ElementPreview {
                ResetIdentityRootView(
                    state = ResetIdentityRootState(
                        displayConfirmationDialog = false,
                        canRecoverFromOtherDevice = true,
                        eventSink = {},
                    ),
                    onContinue = {},
                    onRecoverFromOtherDevice = {},
                    onBack = {},
                )
            }
        }
    }
}

class GuaSecurityScreensEsVerifyTest : GuaSecurityScreensLocaleTest("es")

class GuaSecurityScreensFrVerifyTest : GuaSecurityScreensLocaleTest("fr")
