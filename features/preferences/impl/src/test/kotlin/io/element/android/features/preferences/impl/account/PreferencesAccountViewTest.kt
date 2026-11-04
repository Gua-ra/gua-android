/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class PreferencesAccountViewTest : RobolectricTest() {
    @Test
    fun `click on Manage account invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam("aUrl") { callback ->
            setView(
                aPreferencesAccountState(accountManagementUrl = "aUrl"),
                onManageAccountClick = callback,
            )
            clickOn(CommonStrings.action_manage_account_and_devices)
        }
    }

    @Test
    fun `when accountManagementUrl is null, item is not shown`() = runAndroidComposeUiTest {
        setView(aPreferencesAccountState(accountManagementUrl = null))
        onNodeWithText(activity!!.getString(CommonStrings.action_manage_account_and_devices)).assertDoesNotExist()
    }

    @Test
    fun `click on Two-step verification invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(
                aPreferencesAccountState(),
                onSetupTwoStepVerification = callback,
            )
            clickOn(R.string.screen_two_step_verification_title)
        }
    }

    @Test
    fun `click on Change phone number invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(
                aPreferencesAccountState(),
                onChangePhoneNumber = callback,
            )
            clickOn(R.string.screen_change_phone_title)
        }
    }

    @Test
    fun `click on Link new devices invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(
                aPreferencesAccountState(showLinkNewDevice = true),
                onLinkNewDeviceClick = callback,
            )
            clickOn(CommonStrings.common_link_new_device)
        }
    }

    @Test
    fun `when showLinkNewDevice is false, item is not shown`() = runAndroidComposeUiTest {
        setView(aPreferencesAccountState(showLinkNewDevice = false))
        onNodeWithText(activity!!.getString(CommonStrings.common_link_new_device)).assertDoesNotExist()
    }

    @Test
    fun `click on Encryption invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(
                aPreferencesAccountState(showSecureBackup = true),
                onSecureBackupClick = callback,
            )
            val text = activity!!.getString(CommonStrings.common_encryption)
            onNode(hasText(text) and hasClickAction()).performClick()
        }
    }

    @Test
    fun `when showSecureBackup is false, item is not shown`() = runAndroidComposeUiTest {
        setView(aPreferencesAccountState(showSecureBackup = false))
        onNodeWithText(activity!!.getString(CommonStrings.common_encryption)).assertDoesNotExist()
    }

    @Test
    fun `click on Moderation and safety invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(
                aPreferencesAccountState(),
                onModerationAndSafetyClick = callback,
            )
            val text = activity!!.getString(CommonStrings.common_moderation_and_safety)
            onNode(hasText(text) and hasClickAction()).performClick()
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    state: PreferencesAccountState,
    onManageAccountClick: (url: String) -> Unit = EnsureNeverCalledWithParam(),
    onSetupTwoStepVerification: () -> Unit = EnsureNeverCalled(),
    onChangePhoneNumber: () -> Unit = EnsureNeverCalled(),
    onLinkNewDeviceClick: () -> Unit = EnsureNeverCalled(),
    onModerationAndSafetyClick: () -> Unit = EnsureNeverCalled(),
    onSecureBackupClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        PreferencesAccountView(
            state = state,
            onManageAccountClick = onManageAccountClick,
            onSetupTwoStepVerification = onSetupTwoStepVerification,
            onChangePhoneNumber = onChangePhoneNumber,
            onLinkNewDeviceClick = onLinkNewDeviceClick,
            onModerationAndSafetyClick = onModerationAndSafetyClick,
            onSecureBackupClick = onSecureBackupClick,
        )
    }
}
