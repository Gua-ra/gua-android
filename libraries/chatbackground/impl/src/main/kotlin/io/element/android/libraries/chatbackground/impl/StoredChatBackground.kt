/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoredChatBackground(
    @SerialName("content") val contentJson: String,
    /** Null for no event, or a server reply without one. */
    @SerialName("event_id") val eventId: String?,
    /** The event's server timestamp, or the previous one plus 1 ms for a change sent from this device that the server has not echoed yet. */
    @SerialName("ts") val timestamp: Long,
)
