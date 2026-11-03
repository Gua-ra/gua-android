/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.timeline.item.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import org.junit.Test

class ChatBackgroundContentMappingTest {
    @Test
    fun `the chat background state event carries its content`() {
        val content = StateContent("", OtherState.Custom(CHAT_BACKGROUND_EVENT_TYPE))

        val mapped = content.withChatBackgroundContent {
            """{"type":"$CHAT_BACKGROUND_EVENT_TYPE","state_key":"","content":{"v":1,"kind":"preset","preset":"mint"},"origin_server_ts":1}"""
        }

        assertThat(mapped).isEqualTo(StateContent("", OtherState.ChatBackground("""{"v":1,"kind":"preset","preset":"mint"}""")))
    }

    @Test
    fun `an unreadable event gives a chat background without content`() {
        val content = StateContent("", OtherState.Custom(CHAT_BACKGROUND_EVENT_TYPE))

        assertThat(content.withChatBackgroundContent { null }).isEqualTo(StateContent("", OtherState.ChatBackground(null)))
        assertThat(content.withChatBackgroundContent { "not json" }).isEqualTo(StateContent("", OtherState.ChatBackground(null)))
    }

    @Test
    fun `other custom state, other state keys and other content are left alone`() {
        val other = StateContent("", OtherState.Custom("org.example.custom"))
        val withStateKey = StateContent("key", OtherState.Custom(CHAT_BACKGROUND_EVENT_TYPE))

        assertThat(other.withChatBackgroundContent { error("not read") }).isEqualTo(other)
        assertThat(withStateKey.withChatBackgroundContent { error("not read") }).isEqualTo(withStateKey)
        assertThat(UnknownContent.withChatBackgroundContent { error("not read") }).isEqualTo(UnknownContent)
    }
}
