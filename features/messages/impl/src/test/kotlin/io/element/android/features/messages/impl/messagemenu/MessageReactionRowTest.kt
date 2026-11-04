/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.AggregatedReaction
import io.element.android.features.messages.impl.timeline.model.AggregatedReactionSender
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class MessageReactionRowTest {
    private val me = UserId("@me:gua.global")
    private val camila = UserId("@camila:gua.global")
    private val rafa = UserId("@rafa:gua.global")
    private val bia = UserId("@bia:gua.global")

    @Test
    fun `the current user comes first, then everyone else oldest first`() {
        val reactions = listOf(
            reaction("❤️", sender(rafa, minute = 3), sender(me, minute = 5), sender(camila, minute = 1)),
            reaction("😂", sender(bia, minute = 2)),
        )

        val rows = messageReactionRows(reactions, members = emptyList())

        assertThat(rows.map { it.userId }).containsExactly(me, camila, bia, rafa).inOrder()
        assertThat(rows.map { it.isCurrentUser }).containsExactly(true, false, false, false).inOrder()
    }

    @Test
    fun `ties are ordered by user ID`() {
        val reactions = listOf(reaction("❤️", sender(rafa, minute = 1), sender(bia, minute = 1), sender(camila, minute = 1)))

        val rows = messageReactionRows(reactions, members = emptyList())

        assertThat(rows.map { it.userId }).containsExactly(bia, camila, rafa).inOrder()
    }

    @Test
    fun `each person's reactions are grouped in the order the message shows them`() {
        val reactions = listOf(
            reaction("👍️", sender(camila, minute = 4)),
            reaction("❤️", sender(camila, minute = 1), sender(rafa, minute = 1)),
        )

        val rows = messageReactionRows(reactions, members = emptyList())

        assertThat(rows.map { it.userId to it.keys }).containsExactly(
            camila to listOf("👍️", "❤️"),
            rafa to listOf("❤️"),
        ).inOrder()
    }

    @Test
    fun `long text reactions are shortened like the pills under messages`() {
        val reaction = reaction("this is the best news I have heard all year", sender(rafa, minute = 1))

        val rows = messageReactionRows(listOf(reaction), members = emptyList())

        assertThat(rows.single().keys).containsExactly(reaction.displayKey)
        assertThat(reaction.displayKey.length).isLessThan(reaction.key.length)
    }

    @Test
    fun `names are the member display name, otherwise the handle without the server`() {
        val reactions = listOf(reaction("❤️", sender(camila, minute = 1), sender(rafa, minute = 2)))
        val members = listOf(aRoomMember(userId = camila, displayName = "Camila Moraes", avatarUrl = "mxc://gua/camila"))

        val rows = messageReactionRows(reactions, members)

        assertThat(rows.map { it.name }).containsExactly("Camila Moraes", "@rafa").inOrder()
        assertThat(rows.first().avatarData.url).isEqualTo("mxc://gua/camila")
        assertThat(rows.map { it.name }.none { it.contains(":") }).isTrue()
    }

    @Test
    fun `no reactions means nobody to list`() {
        assertThat(messageReactionRows(emptyList(), members = emptyList())).isEmpty()
    }

    private fun reaction(key: String, vararg senders: AggregatedReactionSender) = AggregatedReaction(
        currentUserId = me,
        key = key,
        senders = senders.toList().toImmutableList(),
    )

    private fun sender(userId: UserId, minute: Long) = AggregatedReactionSender(
        senderId = userId,
        timestamp = minute * 60_000,
        sentTime = "",
    )
}
