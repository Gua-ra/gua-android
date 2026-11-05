/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

data class TimelineItemChatBackgroundContent(
    override val body: String,
) : TimelineItemStateContent {
    override val type: String = "TimelineItemChatBackgroundContent"
}
