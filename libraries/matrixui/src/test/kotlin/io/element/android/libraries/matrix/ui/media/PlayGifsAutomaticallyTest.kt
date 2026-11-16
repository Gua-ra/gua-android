/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.libraries.matrix.ui.media

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class PlayGifsAutomaticallyTest : RobolectricTest() {
    @Test
    fun `readers follow the stored setting as it changes`() = runAndroidComposeUiTest<ComponentActivity> {
        val setting = MutableStateFlow(false)
        setContent {
            CompositionLocalProvider(playGifsAutomaticallyFrom(setting)) {
                BasicText(if (LocalPlayGifsAutomatically.current) "plays" else "still")
            }
        }
        onNodeWithText("still").assertExists()

        setting.value = true
        onNodeWithText("plays").assertExists()

        setting.value = false
        onNodeWithText("still").assertExists()
    }
}
