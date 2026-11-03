/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageMenuLayoutTest {
    private val container = rect(x = 0f, y = 60f, width = 400f, height = 760f)

    @Test
    fun `a bubble with room around it stays where it was`() {
        val source = rect(x = 16f, y = 300f, width = 250f, height = 80f)

        val layout = compute(source, alignsRight = false, container, reactionBarSize = Size(330f, 56f), detailsSize = Size(250f, 200f))

        assertThat(layout.bubble).isEqualTo(source)
        assertThat(layout.reactionBar).isEqualTo(rect(x = 16f, y = 236f, width = 330f, height = 56f))
        assertThat(layout.details).isEqualTo(rect(x = 16f, y = 388f, width = 250f, height = 200f))
    }

    @Test
    fun `a bubble near the top moves down to make room for the reactions`() {
        val source = rect(x = 16f, y = 70f, width = 250f, height = 80f)

        val layout = compute(source, alignsRight = false, container, reactionBarSize = Size(330f, 56f), detailsSize = null)

        assertThat(layout.bubble.top).isEqualTo(60f + 56f + 8f)
        assertThat(layout.reactionBar?.top).isEqualTo(60f)
        assertThat(layout.details).isNull()
    }

    @Test
    fun `an outgoing bubble near the bottom moves up and aligns to its trailing edge`() {
        val source = rect(x = 200f, y = 760f, width = 180f, height = 44f)

        val layout = compute(source, alignsRight = true, container, reactionBarSize = Size(330f, 56f), detailsSize = Size(250f, 300f))

        assertThat(layout.bubble.bottom).isEqualTo(container.bottom - 300f - 8f)
        assertThat(layout.details?.right).isEqualTo(source.right)
        assertThat(layout.reactionBar?.right).isEqualTo(source.right)
    }

    @Test
    fun `the menu stays clear of the screen edges`() {
        val source = rect(x = 230f, y = 300f, width = 168f, height = 44f)

        val layout = compute(source, alignsRight = true, container, reactionBarSize = Size(514f, 56f), detailsSize = Size(250f, 200f))

        assertThat(layout.reactionBar).isEqualTo(rect(x = 12f, y = 236f, width = 376f, height = 56f))
        assertThat(layout.details?.right).isEqualTo(container.right - 12f)
    }

    @Test
    fun `a tall bubble is clipped to the space left`() {
        val source = rect(x = 16f, y = 0f, width = 300f, height = 2000f)

        val layout = compute(source, alignsRight = false, container, reactionBarSize = Size(330f, 56f), detailsSize = Size(250f, 300f))

        assertThat(layout.bubble.top).isEqualTo(60f + 56f + 8f)
        assertThat(layout.bubble.height).isEqualTo(760f - 56f - 8f - 300f - 8f)
    }

    @Test
    fun `landscape keeps the bubble visible and gives the card what is left`() {
        val landscape = rect(x = 0f, y = 8f, width = 850f, height = 356f)
        val source = rect(x = 600f, y = 250f, width = 200f, height = 44f)

        val layout = compute(source, alignsRight = true, landscape, reactionBarSize = Size(330f, 56f), detailsSize = Size(250f, 342f))

        assertThat(layout.bubble.height).isEqualTo(44f)
        assertThat(layout.reactionBar?.top).isEqualTo(landscape.top)
        assertThat(layout.details?.height).isEqualTo(356f - 56f - 8f - 44f - 8f)
        assertThat(layout.details?.bottom).isEqualTo(landscape.bottom)
    }

    @Test
    fun `a bubble outside the screen is pulled back in`() {
        val source = rect(x = 500f, y = 300f, width = 200f, height = 44f)

        val layout = compute(source, alignsRight = true, container, reactionBarSize = null, detailsSize = null)

        assertThat(layout.bubble).isEqualTo(rect(x = 200f, y = 300f, width = 200f, height = 44f))
    }

    private fun compute(
        source: Rect,
        alignsRight: Boolean,
        container: Rect,
        reactionBarSize: Size?,
        detailsSize: Size?,
    ) = MessageMenuLayout.compute(
        source = source,
        alignsRight = alignsRight,
        container = container,
        reactionBarSize = reactionBarSize,
        detailsSize = detailsSize,
        spacing = 8f,
        margin = 12f,
        minimumBubbleHeight = 44f,
    )

    private fun rect(x: Float, y: Float, width: Float, height: Float) = Rect(Offset(x, y), Size(width, height))
}
