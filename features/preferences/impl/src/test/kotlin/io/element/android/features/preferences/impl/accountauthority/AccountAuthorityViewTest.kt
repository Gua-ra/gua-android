/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.accountauthority

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class AccountAuthorityViewTest : RobolectricTest() {
    @Test
    fun `the recovery artifact field tells the IME not to learn what is typed into it`() = runAndroidComposeUiTest {
        setRecoveryEntry()

        onNodeWithText(A_TYPED_ARTIFACT).performClick()
        val editorInfo = editorInfoOfTheFocusedField()

        assertThat(editorInfo.inputType and InputType.TYPE_MASK_VARIATION)
            .isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertThat(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT).isEqualTo(0)
        assertThat(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).isEqualTo(0)
        assertThat(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS).isEqualTo(0)
    }

    private fun AndroidComposeUiTest<ComponentActivity>.editorInfoOfTheFocusedField(): EditorInfo {
        val view = requireNotNull(requireNotNull(activity).window.decorView.findFocus()) {
            "no view holds focus, so nothing was asked of the IME"
        }
        return EditorInfo().also { view.onCreateInputConnection(it) }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setRecoveryEntry() {
        setContent {
            AccountAuthorityView(
                state = aRecoveryEntryState(),
                onBackClick = {},
            )
        }
    }
}

private const val A_TYPED_ARTIFACT = "gua-recovery-1 tvq3 dhpp"

private fun aRecoveryEntryState(): AccountAuthorityState = anAccountAuthorityState(
    phase = AccountAuthorityPhase.RecoveryEntry,
    deviceHoldsAuthority = false,
    thisDeviceKeyB64Url = null,
    recoveryArtifactInput = A_TYPED_ARTIFACT,
    recoveryRoute = AccountAuthorityRecoveryRoute.RecoveryKey,
)
