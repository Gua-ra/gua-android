/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.securebackup.impl.reset.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.securebackup.impl.R
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.pressBackKey
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config

class ResetIdentityRootViewTest : RobolectricTest() {
    @Test
    fun `pressing the back HW button invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce {
            setResetRootView(
                ResetIdentityRootState(displayConfirmationDialog = false, eventSink = {}),
                onBack = it,
            )
            pressBackKey()
        }
    }

    @Test
    fun `clicking on the back navigation button invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce {
            setResetRootView(
                ResetIdentityRootState(displayConfirmationDialog = false, eventSink = {}),
                onBack = it,
            )
            pressBack()
        }
    }

    @Test
    @Config(qualifiers = "h720dp")
    fun `clicking the reset button goes straight through`() = runAndroidComposeUiTest {
        // GUA FORK: no second confirmation. This screen already names what is lost and its button
        // is destructive, so the "are you sure you want to reset your digital identity?" dialog
        // that used to sit in between was jargon on top of a confirmation the user had just given.
        ensureCalledOnce {
            setResetRootView(
                ResetIdentityRootState(displayConfirmationDialog = false, eventSink = {}),
                onContinue = it,
            )
            clickOn(R.string.gua_encryption_reset_required_action)
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setResetRootView(
    state: ResetIdentityRootState,
    onBack: () -> Unit = EnsureNeverCalled(),
    onContinue: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        ResetIdentityRootView(state = state, onContinue = onContinue, onBack = onBack)
    }
}
