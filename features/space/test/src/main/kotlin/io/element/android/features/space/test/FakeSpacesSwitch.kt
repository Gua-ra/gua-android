/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.test

import io.element.android.features.space.api.SpacesSwitch
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSpacesSwitch(
    initialValue: Boolean = false,
    private val setEnabledResult: (Boolean) -> Result<Unit> = { Result.success(Unit) },
) : SpacesSwitch {
    private val state = MutableStateFlow(initialValue)
    override val isEnabled: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        return setEnabledResult(enabled).onSuccess { state.value = enabled }
    }

    val enableWhenJoinedCalls = mutableListOf<RoomId>()

    override fun enableWhenJoined(roomId: RoomId) {
        enableWhenJoinedCalls += roomId
    }
}
