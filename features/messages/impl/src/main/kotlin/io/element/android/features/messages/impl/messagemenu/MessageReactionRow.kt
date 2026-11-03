/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import io.element.android.features.messages.impl.timeline.model.AggregatedReaction
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.getBestName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class MessageReactionRow(
    val userId: UserId,
    val isCurrentUser: Boolean,
    val name: String,
    val avatarData: AvatarData,
    val keys: ImmutableList<String>,
)

fun messageReactionRows(
    reactions: List<AggregatedReaction>,
    members: List<RoomMember>,
): ImmutableList<MessageReactionRow> {
    val keysByUser = LinkedHashMap<UserId, MutableList<String>>()
    val earliestByUser = HashMap<UserId, Long>()
    var currentUserId: UserId? = null
    reactions.forEach { reaction ->
        reaction.senders.forEach { sender ->
            val keys = keysByUser.getOrPut(sender.senderId) { mutableListOf() }
            if (reaction.displayKey !in keys) {
                keys.add(reaction.displayKey)
            }
            earliestByUser[sender.senderId] = minOf(earliestByUser[sender.senderId] ?: sender.timestamp, sender.timestamp)
            if (sender.senderId == reaction.currentUserId) {
                currentUserId = sender.senderId
            }
        }
    }
    val membersById = members.associateBy { it.userId }
    val order = compareByDescending<UserId> { it == currentUserId }
        .thenBy { earliestByUser[it] }
        .thenBy { it.value }
    return keysByUser.keys
        .sortedWith(order)
        .map { userId ->
            val member = membersById[userId]
            MessageReactionRow(
                userId = userId,
                isCurrentUser = userId == currentUserId,
                name = member?.getBestName() ?: userId.displayHandle,
                avatarData = AvatarData(
                    id = userId.value,
                    name = member?.displayName,
                    url = member?.avatarUrl,
                    size = AvatarSize.TimelineSender,
                ),
                keys = keysByUser.getValue(userId).toImmutableList(),
            )
        }
        .toImmutableList()
}
