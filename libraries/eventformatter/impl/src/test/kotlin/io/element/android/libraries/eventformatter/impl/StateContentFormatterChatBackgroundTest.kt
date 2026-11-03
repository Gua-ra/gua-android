/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.eventformatter.impl

import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.eventformatter.impl.mode.RenderingMode
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.services.toolbox.impl.strings.AndroidStringProvider
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "en")
class StateContentFormatterChatBackgroundTest : RobolectricTest() {
    private val formatter = StateContentFormatter(AndroidStringProvider(InstrumentationRegistry.getInstrumentation().targetContext.resources))

    private fun format(content: OtherState, senderIsYou: Boolean, renderingMode: RenderingMode = RenderingMode.Timeline) =
        formatter.format(StateContent("", content), senderDisambiguatedDisplayName = "Alice", senderIsYou = senderIsYou, renderingMode = renderingMode)

    @Test
    fun `a changed background names who changed it`() {
        val changed = OtherState.ChatBackground("""{"v":1,"kind":"preset","preset":"mint"}""")

        assertThat(format(changed, senderIsYou = true)).isEqualTo("You changed the chat background")
        assertThat(format(changed, senderIsYou = false)).isEqualTo("Alice changed the chat background")
    }

    @Test
    fun `empty content is a removal`() {
        val removed = OtherState.ChatBackground("{ }")

        assertThat(format(removed, senderIsYou = true)).isEqualTo("You removed the chat background")
        assertThat(format(removed, senderIsYou = false)).isEqualTo("Alice removed the chat background")
    }

    @Test
    fun `unreadable content reads as a change`() {
        assertThat(format(OtherState.ChatBackground(null), senderIsYou = false)).isEqualTo("Alice changed the chat background")
    }

    @Test
    fun `the notice is not shown in the chat list`() {
        assertThat(format(OtherState.ChatBackground("{}"), senderIsYou = false, renderingMode = RenderingMode.RoomList)).isNull()
    }

    @Test
    fun `other custom state is never shown`() {
        assertThat(format(OtherState.Custom("org.example.custom"), senderIsYou = false)).isNull()
        assertThat(format(OtherState.Custom("org.example.custom"), senderIsYou = false, renderingMode = RenderingMode.RoomList)).isNull()
    }
}
