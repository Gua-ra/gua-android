/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room

import io.element.android.libraries.matrix.api.room.powerlevels.RoomPermissions

/** The room state event, with an empty state key, that holds the background everyone in a chat sees. */
const val CHAT_BACKGROUND_EVENT_TYPE = "global.gua.room.background"

fun RoomPermissions.canChangeChatBackground(): Boolean = canOwnUserSendState(StateEventType.Custom(CHAT_BACKGROUND_EVENT_TYPE))
