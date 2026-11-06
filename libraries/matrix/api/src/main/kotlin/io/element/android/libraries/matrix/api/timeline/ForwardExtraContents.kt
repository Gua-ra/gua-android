/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline

import io.element.android.libraries.matrix.api.core.RoomId

// Asked once per chat forwarded to: one JSON object per forwarded event, whose fields are added at the top level of its content, or null for none.
fun interface ForwardExtraContents {
    fun forChat(roomId: RoomId): List<String?>

    // Called when forwarding to a chat stops before the event at fromIndex is sent; that event and the ones after it may never reach the chat.
    fun onUnsent(roomId: RoomId, fromIndex: Int) {}
}
