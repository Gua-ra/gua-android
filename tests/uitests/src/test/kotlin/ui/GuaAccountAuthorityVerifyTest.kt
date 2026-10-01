/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.features.preferences.impl.accountauthority.AN_INSTALL_ID
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityPhase
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityRecoveryRoute
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityState
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityStepUp
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityStepUpBlock
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityStepUpMethod
import io.element.android.features.preferences.impl.accountauthority.AccountAuthorityView
import io.element.android.features.preferences.impl.accountauthority.aPendingTransition
import io.element.android.features.preferences.impl.accountauthority.aQuarantinedDevice
import io.element.android.features.preferences.impl.accountauthority.aRootedChain
import io.element.android.features.preferences.impl.accountauthority.aSecurityNotificationView
import io.element.android.features.preferences.impl.accountauthority.anAccountAuthorityState
import io.element.android.features.preferences.impl.accountauthority.anAuthorityApproval
import io.element.android.features.preferences.impl.accountauthority.anAuthorityDevice
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.guaresolver.authority.SecurityNotificationView
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

class GuaAccountAuthorityVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    private val defaultTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    fun pinTheClockToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTheClock() {
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun guaAccountAuthorityOverview() {
        snapshot(
            anAccountAuthorityState(
                chain = aRootedChain(
                    devices = listOf(anAuthorityDevice(), anAuthorityDevice(deviceKeyB64Url = "second-key", label = "iPhone 16", grantedSeq = 2)),
                ),
                canRecoverThroughAccountRecovery = true,
                approvals = listOf(anAuthorityApproval()),
                notificationChannelAvailable = true,
                securityNotifications = listOf(
                    aSecurityNotificationView(),
                    aSecurityNotificationView(installationId = "another-install", deviceLabel = "iPhone 16", tokenFingerprint = "4c1d"),
                ),
            )
        )
    }

    @Test
    fun guaAccountAuthorityQuarantineAndPending() {
        snapshot(
            anAccountAuthorityState(
                chain = aRootedChain(
                    devices = listOf(anAuthorityDevice(), aQuarantinedDevice()),
                    pending = aPendingTransition(),
                ),
                notificationChannelAvailable = true,
                securityNotifications = listOf(aSecurityNotificationView()),
            )
        )
    }

    @Test
    fun guaAccountAuthoritySecurityAlerts() {
        snapshot(anAlertsState())
    }

    @Test
    fun guaAccountAuthorityWithoutTheChannel() {
        snapshot(anAlertsState(channelAvailable = false, registrations = emptyList()))
    }

    @Test
    fun guaAccountAuthorityGenesisAccountHasNoWeakerRoute() {
        snapshot(
            anAlertsState(accountClass = "GENESIS", canRecoverThroughAccountRecovery = false)
        )
    }

    @Test
    fun guaAccountAuthorityRecoveryEntry() {
        snapshot(
            anAccountAuthorityState(
                phase = AccountAuthorityPhase.RecoveryEntry,
                recoveryRoute = AccountAuthorityRecoveryRoute.RecoveryKey,
                recoveryArtifactInput = "GUA-RECOVERY-1 TVQ3 DHPP 7VNG BOUE",
            )
        )
    }

    @Test
    fun guaAccountAuthorityRemovalBlockedForAPasskeyOnlyAccount() {
        snapshot(
            anAccountAuthorityState(
                phase = AccountAuthorityPhase.StepUp,
                stepUp = AccountAuthorityStepUp.RemoveNotification,
                stepUpBlock = AccountAuthorityStepUpBlock.PasskeyNotUsableForNotificationRemoval,
                notificationChannelAvailable = true,
                securityNotifications = listOf(aSecurityNotificationView()),
                notificationRemovalTarget = aSecurityNotificationView(),
            )
        )
    }

    @Test
    fun guaAccountAuthorityRemovalStepUp() {
        snapshot(
            anAccountAuthorityState(
                phase = AccountAuthorityPhase.StepUp,
                stepUp = AccountAuthorityStepUp.RemoveNotification,
                stepUpMethod = AccountAuthorityStepUpMethod.Pin,
                pin = "1234",
                notificationChannelAvailable = true,
                securityNotifications = listOf(aSecurityNotificationView(installationId = AN_INSTALL_ID)),
                notificationRemovalTarget = aSecurityNotificationView(installationId = AN_INSTALL_ID),
            )
        )
    }

    private fun anAlertsState(
        channelAvailable: Boolean = true,
        registrations: List<SecurityNotificationView> = listOf(
            aSecurityNotificationView(),
            aSecurityNotificationView(installationId = "another-install", deviceLabel = "iPhone 16", tokenFingerprint = "4c1d"),
        ),
        accountClass: String = "BOOTSTRAP",
        canRecoverThroughAccountRecovery: Boolean = true,
    ) = anAccountAuthorityState(
        chain = aRootedChain(devices = emptyList(), accountClass = accountClass),
        canRecoverThroughAccountRecovery = canRecoverThroughAccountRecovery,
        notificationChannelAvailable = channelAvailable,
        securityNotifications = registrations,
    )

    private fun snapshot(state: AccountAuthorityState) {
        paparazzi.snapshot {
            ElementPreview {
                AccountAuthorityView(
                    state = state,
                    onBackClick = {},
                )
            }
        }
    }
}
