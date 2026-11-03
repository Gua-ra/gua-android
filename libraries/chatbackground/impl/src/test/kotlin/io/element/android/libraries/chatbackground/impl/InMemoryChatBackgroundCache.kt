/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import io.element.android.libraries.matrix.api.core.RoomId

class InMemoryChatBackgroundCache(initial: Map<RoomId, StoredChatBackground> = emptyMap()) : ChatBackgroundCache {
    val entries = LinkedHashMap(initial)

    override suspend fun load(): Map<RoomId, StoredChatBackground> = LinkedHashMap(entries)

    override suspend fun save(roomId: RoomId, stored: StoredChatBackground) {
        entries[roomId] = stored
    }
}
