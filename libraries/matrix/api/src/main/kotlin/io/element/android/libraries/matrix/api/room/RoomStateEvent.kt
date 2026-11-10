/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room

import io.element.android.libraries.matrix.api.core.EventId

data class RoomStateEvent(
    val stateKey: String,
    val contentJson: String,
    /** Null when the SDK does not know the event that set this state. */
    val eventId: EventId?,
    /** The event's `origin_server_ts`, null when unknown. */
    val timestamp: Long?,
)
