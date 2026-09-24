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

/**
 * GUA FORK: what the account authority screen asks the platform for (ADM-009).
 *
 * One test, for the one field on this screen that carries key material. The artifact typed into it is the
 * PRIVATE recovery authority key, and the IME defaults would treat those 52 base32 characters as prose: it
 * learns them, and Gboard backs its learned dictionary up to the signed-in Google account. "Never synced,
 * never backed up" is the property ADM-008 decision 5 and the keystore claim for this material, so the field
 * has to say so to the IME rather than rely on the user not having learning on.
 */
class AccountAuthorityViewTest : RobolectricTest() {
    @Test
    fun `the recovery artifact field tells the IME not to learn what is typed into it`() = runAndroidComposeUiTest {
        setRecoveryEntry()

        // Focused, because the IME contract is only asked for when there is something to type into.
        onNodeWithText(A_TYPED_ARTIFACT).performClick()
        val editorInfo = editorInfoOfTheFocusedField()

        // A password field is how this platform says "do not learn this": Gboard turns personalised learning
        // and suggestions off for one, which is the whole point of asking.
        assertThat(editorInfo.inputType and InputType.TYPE_MASK_VARIATION)
            .isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
        // And no autocorrect, which would rewrite base32 groups into words on its own.
        assertThat(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT).isEqualTo(0)
        // No capitalisation either. The reader forgives ASCII case now, so this is no longer what stands
        // between an autocapitalised first character and a refusal, but the artifact is shown in lowercase and
        // a field that quietly disagrees with the screen it was copied from is a difference nobody can see.
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

/** The recovery-entry screen, with something typed in so the field can be found by its own value. */
private fun aRecoveryEntryState(): AccountAuthorityState = anAccountAuthorityState(
    phase = AccountAuthorityPhase.RecoveryEntry,
    deviceHoldsAuthority = false,
    thisDeviceKeyB64Url = null,
    recoveryArtifactInput = A_TYPED_ARTIFACT,
    recoveryRoute = AccountAuthorityRecoveryRoute.RecoveryKey,
)
