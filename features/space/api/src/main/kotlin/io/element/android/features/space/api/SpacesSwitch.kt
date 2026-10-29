/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.api

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Spaces section is shown. Stored in account data so it follows the account to every phone.
 */
interface SpacesSwitch {
    val isEnabled: StateFlow<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>

    /**
     * Re-reads the stored value. Never decides the default for an account with no stored value.
     */
    suspend fun refresh()

    /**
     * Turns Spaces on once the current user's membership in [roomId] becomes joined. Outlives the calling screen.
     */
    fun enableWhenJoined(roomId: RoomId)
}
