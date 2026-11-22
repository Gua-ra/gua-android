/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.matrix.api.room.join.JoinRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ChatThemePrecedenceTest {
    @Test
    fun `the shared precedence fixtures give the expected theme`() {
        val fixtures = javaClass.classLoader!!.getResource("chat-theme-precedence-fixtures.json")!!.readText()
        val cases = Json.parseToJsonElement(fixtures).jsonObject.getValue("cases").jsonArray

        assertThat(cases).isNotEmpty()
        cases.forEach { element ->
            val case = element.jsonObject
            val effective = effectiveChatTheme(
                chatTheme = ChatBackgroundCodec.decode(case.getValue("chatTheme").toString()),
                defaultTheme = ChatBackgroundCodec.decode(case.getValue("defaultTheme").toString()),
                roomIsPrivate = case.getValue("roomIsPrivate").jsonPrimitive.boolean,
                mediaShown = case.getValue("mediaShown").jsonPrimitive.boolean,
            )
            assertWithMessage(case.getValue("name").jsonPrimitive.content).that(effective.toFixture()).isEqualTo(case.getValue("expected").normalized())
        }
    }

    @Test
    fun `invite only and knock rooms are private chats, public ones are not`() {
        assertThat(JoinRule.Invite.isPrivateChat()).isTrue()
        assertThat(JoinRule.Knock.isPrivateChat()).isTrue()
        assertThat(JoinRule.Public.isPrivateChat()).isFalse()
        assertThat((null as JoinRule?).isPrivateChat()).isFalse()
    }
}
