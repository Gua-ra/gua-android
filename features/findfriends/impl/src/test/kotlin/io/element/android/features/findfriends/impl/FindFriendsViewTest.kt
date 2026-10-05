/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Density
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
class FindFriendsViewTest : RobolectricTest() {
    @Config(qualifiers = "fr-w360dp-h640dp")
    @Test
    fun `the allow access action stays reachable at a large font scale`() = runAndroidComposeUiTest {
        setFindFriendsView(phase = FindFriendsPhase.NeedsPermission, fontScale = 2f)
        onNodeWithText(activity!!.getString(R.string.screen_find_friends_permission_action))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Config(qualifiers = "es-w360dp-h640dp")
    @Test
    fun `the open settings action stays reachable at a large font scale`() = runAndroidComposeUiTest {
        setFindFriendsView(phase = FindFriendsPhase.PermissionDenied, fontScale = 2f)
        onNodeWithText(activity!!.getString(R.string.screen_find_friends_permission_denied_action))
            .performScrollTo()
            .assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun AndroidComposeUiTest<ComponentActivity>.setFindFriendsView(
    phase: FindFriendsPhase,
    fontScale: Float,
) {
    setContent {
        CompositionLocalProvider(
            LocalDensity provides Density(density = LocalDensity.current.density, fontScale = fontScale),
        ) {
            FindFriendsView(
                state = FindFriendsState(
                    phase = phase,
                    contacts = persistentListOf(),
                    startingChatUserId = null,
                    eventSink = {},
                ),
                onBackClick = {},
            )
        }
    }
}
