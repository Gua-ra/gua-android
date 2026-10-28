/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.element.android.appnav.LoggedInFlowNode.NavTarget
import io.element.android.features.securebackup.api.SecureBackupEntryPoint
import io.element.android.libraries.matrix.api.core.FlowId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.verification.SessionVerificationRequestDetails
import io.element.android.libraries.matrix.api.verification.VerificationRequest
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_TIMESTAMP
import io.element.android.libraries.matrix.test.A_USER_ID
import org.junit.Test

class NewSignInGateTest {
    @Test
    fun `the chat list and a chat are main screens`() {
        assertThat(isShowingMainScreens(Lifecycle.State.RESUMED, NavTarget.Home)).isTrue()
        assertThat(isShowingMainScreens(Lifecycle.State.RESUMED, NavTarget.Room(A_ROOM_ID.toRoomIdOrAlias()))).isTrue()
    }

    @Test
    fun `flows covering the main screens are not main screens`() {
        listOf(
            NavTarget.Placeholder,
            NavTarget.IncomingVerificationRequest(anOwnDeviceRequest),
            NavTarget.Ftue,
            NavTarget.SecureBackup(SecureBackupEntryPoint.InitialTarget.ResetIdentity),
            NavTarget.Settings(),
            null,
        ).forEach { target ->
            assertThat(isShowingMainScreens(Lifecycle.State.RESUMED, target)).isFalse()
        }
    }

    @Test
    fun `the chat list is not shown while a root flow covers the logged-in flow`() {
        listOf(Lifecycle.State.CREATED, Lifecycle.State.STARTED, Lifecycle.State.DESTROYED).forEach { state ->
            assertThat(isShowingMainScreens(state, NavTarget.Home)).isFalse()
        }
    }

    @Test
    fun `only a request from another device of this account is recorded`() {
        assertThat(anOwnDeviceRequest.ownDeviceIdOrNull()).isEqualTo(A_DEVICE_ID)
        assertThat(VerificationRequest.Incoming.User(aRequestDetails).ownDeviceIdOrNull()).isNull()
    }

    private val aRequestDetails = SessionVerificationRequestDetails(
        senderProfile = MatrixUser(userId = A_USER_ID),
        flowId = FlowId("flowId"),
        deviceId = A_DEVICE_ID,
        deviceDisplayName = "a device name",
        firstSeenTimestamp = A_TIMESTAMP,
    )

    private val anOwnDeviceRequest = VerificationRequest.Incoming.OtherSession(aRequestDetails)
}
