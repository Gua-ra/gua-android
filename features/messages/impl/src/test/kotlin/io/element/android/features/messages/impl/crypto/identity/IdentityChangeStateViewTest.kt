/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.crypto.identity

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.designsystem.components.avatar.anAvatarData
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.ui.room.IdentityRoomMember
import io.element.android.libraries.matrix.ui.room.RoomMemberIdentityStateChange
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class IdentityChangeStateViewTest : RobolectricTest() {
    @Test
    fun `show and resolve pin violation`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IdentityChangeEvent>()
        setIdentityChangeStateView(
            state = anIdentityChangeState(
                listOf(
                    RoomMemberIdentityStateChange(
                        identityRoomMember = IdentityRoomMember(UserId("@alice:localhost"), "Alice", anAvatarData()),
                        identityState = IdentityState.PinViolation
                    )
                ),
                eventsRecorder
            ),
        )

        onNodeWithText("security details changed", substring = true).assertExists("should display the identity change notice")
        onNodeWithText("@alice:localhost", substring = true).assertDoesNotExist()
        onNodeWithText("Alice", substring = true).assertExists("should display user displayname")

        clickOn(res = CommonStrings.action_ok)
        eventsRecorder.assertSingle(IdentityChangeEvent.PinIdentity(UserId("@alice:localhost")))
    }

    @Test
    fun `show and resolve verification violation`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IdentityChangeEvent>()
        setIdentityChangeStateView(
            state = anIdentityChangeState(
                listOf(
                    RoomMemberIdentityStateChange(
                        identityRoomMember = IdentityRoomMember(UserId("@alice:localhost"), "Alice", anAvatarData()),
                        identityState = IdentityState.VerificationViolation
                    )
                ),
                eventsRecorder
            ),
        )

        onNodeWithText("security details changed", substring = true).assertExists("should display the identity change notice")
        onNodeWithText("@alice:localhost", substring = true).assertDoesNotExist()
        onNodeWithText("Alice", substring = true).assertExists("should display user displayname")

        clickOn(res = CommonStrings.action_ok)
        eventsRecorder.assertSingle(IdentityChangeEvent.WithdrawVerification(UserId("@alice:localhost")))
    }

    @Test
    fun `Should not show any banner if no violations`() = runAndroidComposeUiTest {
        setIdentityChangeStateView(
            state = anIdentityChangeState(
                listOf(
                    RoomMemberIdentityStateChange(
                        identityRoomMember = IdentityRoomMember(UserId("@alice:localhost"), "Alice", anAvatarData()),
                        identityState = IdentityState.Verified
                    ),
                    RoomMemberIdentityStateChange(
                        identityRoomMember = IdentityRoomMember(UserId("@bob:localhost"), "Bob", anAvatarData()),
                        identityState = IdentityState.Pinned
                    )
                ),
            ),
        )

        onNodeWithText("security details changed", substring = true).assertDoesNotExist()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setIdentityChangeStateView(
        state: IdentityChangeState,
    ) {
        setContent {
            IdentityChangeStateView(
                state = state,
                onLinkClick = { _, _ -> },
            )
        }
    }
}
