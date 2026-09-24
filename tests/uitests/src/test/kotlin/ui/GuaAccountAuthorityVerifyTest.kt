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

/**
 * GUA FORK verification: the account authority screen of ADM-009, which had no rendered coverage at all.
 *
 * Everything this screen decides it decides in the view, from state the presenter hands it: whether a device
 * is drawn as quarantined, whether the two-device carve-out footer appears, whether the weaker recovery route
 * is offered, whether the security-alerts rows are there and what a blocked step-up says instead of a PIN
 * field. A presenter test proves the flags; only a rendered one proves the screen reads them.
 *
 * Like [GuaFindFriendsVerifyTest], this records to its own snapshot files and adds no preview, so the shared
 * preview-driven golden set is not re-sharded.
 */
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

    /**
     * UTC for the duration of these tests, because two of these screens render an absolute date.
     *
     * Without it the golden is a picture of the recording machine's offset: CI would draw "Jan 2" where a
     * laptop in Toronto drew "Jan 1" and the comparison would fail for a reason that has nothing to do with
     * the screen.
     */
    @Before
    fun pinTheClockToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTheClock() {
        TimeZone.setDefault(defaultTimeZone)
    }

    /**
     * The overview of a rooted account with everything on it: two active devices, so decision 5's carve-out
     * applies, a browser approval waiting, the weaker recovery route offered, and the channel of gate 2 with
     * this install's own row in it.
     */
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

    /**
     * A device inside its own grant window, next to a transition inside its.
     *
     * A quarantined device drawn as active would be the screen telling the owner they hold two devices when,
     * for every rule that matters, they hold one, and the pending row is the only thing they can act on.
     */
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

    /**
     * The security-notification channel of gate 2, listed and removable, which this platform had no surface
     * for at all.
     *
     * The device list is empty in this one so the section is above the fold: Paparazzi snapshots a viewport
     * and rescales anything taller, so a page-length shot of a long screen is neither legible nor evidence.
     * This is the shot that proves the rows exist, that this install's own is marked, and that its removal
     * offers exactly what every other row's does.
     */
    @Test
    fun guaAccountAuthoritySecurityAlerts() {
        snapshot(anAlertsState())
    }

    /**
     * The same account on a deployment with the chain on and the channel off.
     *
     * The section is absent rather than empty, because an empty list would read as "your account has nowhere
     * to be warned" when the truth is that this server has the feature switched off. The pair with
     * [guaAccountAuthoritySecurityAlerts] is what makes that difference visible.
     */
    @Test
    fun guaAccountAuthorityWithoutTheChannel() {
        snapshot(anAlertsState(channelAvailable = false, registrations = emptyList()))
    }

    /**
     * A class 0x01 account, whose id commits its own authority.
     *
     * Decision 3 rule 3 refuses the account-recovery route on one outright, so the "I do not have my recovery
     * key" row is absent here and present in [guaAccountAuthoritySecurityAlerts]. That difference is the fix,
     * and this pair is where it shows.
     */
    @Test
    fun guaAccountAuthorityGenesisAccountHasNoWeakerRoute() {
        snapshot(
            anAlertsState(accountClass = "GENESIS", canRecoverThroughAccountRecovery = false)
        )
    }

    /** Typing the artifact back, whose copy no longer tells anyone to use lower case. */
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

    /**
     * The step-up a passkey-only account cannot produce for a removal.
     *
     * No PIN field, no sheet, and no suggestion to add a factor: the copy says what is missing and stops,
     * which is the honest answer on a platform with no native assertion and an endpoint with no sheet.
     */
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

    /** Confirming a removal with the PIN, which is the price decision 13 puts on every row alike. */
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

    /**
     * A rooted account with no device rows and no approvals, so what is left is the recovery routes and the
     * channel. Nothing about the account is unusual: the device list is simply not what these shots are for.
     */
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
