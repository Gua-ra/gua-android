/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.api

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.StateFlow

interface SpacesSwitch {
    val isEnabled: StateFlow<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>

    /** Re-reads the stored value without deciding a default. */
    suspend fun refresh()

    /** Turns Spaces on once the user has joined [roomId], even after the calling screen closes. */
    fun enableWhenJoined(roomId: RoomId)
}
