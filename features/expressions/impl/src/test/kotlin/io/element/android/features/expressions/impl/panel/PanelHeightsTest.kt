/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PanelHeightsTest {
    @Test
    fun `the panel opens at 60 percent of the screen, or at the keyboard height when that is taller`() {
        val heights = PanelHeights.of(keyboardHeight = 300.dp, screenHeight = 800.dp)
        assertThat(heights.opening.value).isWithin(0.01f).of(480f)
        assertThat(heights.expanded.value).isWithin(0.01f).of(680f)
        assertThat(PanelHeights.of(keyboardHeight = 520.dp, screenHeight = 800.dp).opening).isEqualTo(520.dp)
    }

    @Test
    fun `a drag settles at the opening or expanded height, and below the opening height it closes`() {
        val heights = PanelHeights(opening = 480.dp, expanded = 680.dp)

        assertThat(heights.settle(479.dp)).isNull()
        assertThat(heights.settle(560.dp)).isEqualTo(480.dp)
        assertThat(heights.settle(600.dp)).isEqualTo(680.dp)
    }
}
