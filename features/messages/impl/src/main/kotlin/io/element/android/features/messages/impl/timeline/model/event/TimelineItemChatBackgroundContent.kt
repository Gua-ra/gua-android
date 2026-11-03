/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

/** "Alice changed the chat background": shown in 1:1 chats too, and never collapsed with other changes. */
data class TimelineItemChatBackgroundContent(
    override val body: String,
) : TimelineItemStateContent {
    override val type: String = "TimelineItemChatBackgroundContent"
}
