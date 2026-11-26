/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room

import io.element.android.libraries.matrix.api.room.powerlevels.RoomPermissions

/** A room state event whose state key is always empty. */
const val CHAT_BACKGROUND_EVENT_TYPE = "global.gua.room.background"

fun RoomPermissions.canChangeChatBackground(): Boolean = canOwnUserSendState(StateEventType.Custom(CHAT_BACKGROUND_EVENT_TYPE))
