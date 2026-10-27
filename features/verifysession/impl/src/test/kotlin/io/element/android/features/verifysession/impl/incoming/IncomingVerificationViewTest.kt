/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.verifysession.impl.incoming

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.verifysession.impl.R
import io.element.android.features.verifysession.impl.ui.aEmojisSessionVerificationData
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.pressBackKey
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class IncomingVerificationViewTest : RobolectricTest() {
    // region step Initial
    @Test
    fun `back key pressed - ignore the verification`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = aStepInitial(),
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    @Test
    fun `ignore incoming verification emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = aStepInitial(),
                eventSink = eventsRecorder
            ),
        )
        clickOn(CommonStrings.action_ignore)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.IgnoreVerification)
    }

    @Test
    fun `start incoming verification emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = aStepInitial(),
                eventSink = eventsRecorder
            ),
        )
        clickOn(CommonStrings.action_start_verification)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.StartVerification)
    }

    @Test
    fun `back key pressed - when awaiting response cancels the verification`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = aStepInitial(
                    isWaiting = true,
                ),
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }
    // endregion step Initial

    // region step Verifying
    @Test
    fun `back key pressed - when ready to verify cancels the verification`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Verifying(
                    data = aEmojisSessionVerificationData(),
                    isWaiting = false,
                ),
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    @Test
    fun `back key pressed - when verifying and loading emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Verifying(
                    data = aEmojisSessionVerificationData(),
                    isWaiting = true,
                ),
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    @Test
    fun `clicking on they do not match emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Verifying(
                    data = aEmojisSessionVerificationData(),
                    isWaiting = false,
                ),
                eventSink = eventsRecorder
            ),
        )
        clickOn(R.string.screen_session_verification_they_dont_match)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.DeclineVerification)
    }

    @Test
    fun `clicking on they match emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Verifying(
                    data = aEmojisSessionVerificationData(),
                    isWaiting = false,
                ),
                eventSink = eventsRecorder
            ),
        )
        clickOn(R.string.screen_session_verification_they_match)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.ConfirmVerification)
    }
    // endregion

    // region step Failure
    @Test
    fun `back key pressed - when failure resets the flow`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Failure,
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    @Test
    fun `click on done - when failure resets the flow`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Failure,
                eventSink = eventsRecorder
            ),
        )
        clickOn(CommonStrings.action_done)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    // endregion

    // region step Completed
    @Test
    fun `back key pressed - on Completed step emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Completed,
                eventSink = eventsRecorder
            ),
        )
        pressBackKey()
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }

    @Test
    fun `when flow is completed and the user clicks on the done button, the expected event is emitted`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<IncomingVerificationViewEvents>()
        setIncomingVerificationView(
            anIncomingVerificationState(
                step = IncomingVerificationState.Step.Completed,
                eventSink = eventsRecorder
            ),
        )
        clickOn(CommonStrings.action_done)
        eventsRecorder.assertSingle(IncomingVerificationViewEvents.GoBack)
    }
    // endregion

    @Test
    fun `a request from another device of this account reads as a new sign-in`() = runAndroidComposeUiTest<ComponentActivity> {
        setIncomingVerificationView(anIncomingVerificationState(step = aStepInitial()))
        onNodeWithText(activity!!.getString(R.string.gua_verification_new_sign_in_title)).assertExists()
        onNodeWithText(activity!!.getString(R.string.gua_verification_new_sign_in_subtitle)).assertExists()
    }

    @Test
    fun `confirming a new sign-in of this account says where the messages will show`() = runAndroidComposeUiTest<ComponentActivity> {
        setIncomingVerificationView(anIncomingVerificationState(step = IncomingVerificationState.Step.Completed))
        onNodeWithText(activity!!.getString(R.string.gua_verification_new_sign_in_complete_subtitle)).assertExists()
    }

    @Test
    fun `comparing emojis with another device of this account uses the Gua subtitle`() = runAndroidComposeUiTest<ComponentActivity> {
        setIncomingVerificationView(
            anIncomingVerificationState(step = IncomingVerificationState.Step.Verifying(data = aEmojisSessionVerificationData(), isWaiting = false))
        )
        onNodeWithText(activity!!.getString(R.string.gua_verification_compare_emojis_subtitle)).assertExists()
    }

    @Test
    fun `waiting for the other device of this account after the emoji check keeps the Gua subtitle`() = runAndroidComposeUiTest<ComponentActivity> {
        setIncomingVerificationView(
            anIncomingVerificationState(step = IncomingVerificationState.Step.Verifying(data = aEmojisSessionVerificationData(), isWaiting = true))
        )
        onNodeWithText(activity!!.getString(R.string.gua_verification_compare_emojis_subtitle)).assertExists()
    }

    @Test
    fun `a request from another user keeps the user verification copy`() = runAndroidComposeUiTest<ComponentActivity> {
        setIncomingVerificationView(anIncomingVerificationState(step = aStepInitial(), verificationRequest = anIncomingUserVerificationRequest()))
        onNodeWithText(activity!!.getString(R.string.screen_session_verification_request_title)).assertExists()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setIncomingVerificationView(
        state: IncomingVerificationState,
    ) {
        setContent {
            IncomingVerificationView(
                state = state,
            )
        }
    }
}
