/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.space.api.SpacesSwitch
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val SPACES_ACCOUNT_DATA_TYPE = "global.gua.spaces"

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSpacesSwitch(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) : SpacesSwitch {
    private val state = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = state.asStateFlow()

    init {
        // Sync restarts each time the app comes to the foreground, which picks up changes made on another phone.
        matrixClient.syncService.syncState
            .filter { it == SyncState.Running }
            .onEach { readStored()?.let { state.value = it } }
            .launchIn(sessionCoroutineScope)

        // An account with no stored value that is already in a space starts with Spaces on. This waits for the
        // spaces to sync, so a fresh sign-in never writes a value before the stored one has arrived.
        matrixClient.spaceService.topLevelSpacesFlow
            .map { spaces -> spaces.any { it.state == CurrentUserMembership.JOINED } }
            .distinctUntilChanged()
            .filter { it }
            .onEach { enableIfUnset() }
            .launchIn(sessionCoroutineScope)
    }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val previous = state.value
        state.value = enabled
        return matrixClient.setAccountData(SPACES_ACCOUNT_DATA_TYPE, """{"enabled":$enabled}""")
            .onFailure { state.value = previous }
    }

    private suspend fun enableIfUnset() {
        val stored = matrixClient.getAccountData(SPACES_ACCOUNT_DATA_TYPE).getOrElse { return }
        if (stored == null) {
            setEnabled(true)
        } else {
            state.value = parseEnabled(stored)
        }
    }

    /** Returns the stored value, or null when it is unset or cannot be read. */
    private suspend fun readStored(): Boolean? {
        return matrixClient.getAccountData(SPACES_ACCOUNT_DATA_TYPE).getOrNull()?.let(::parseEnabled)
    }

    private fun parseEnabled(content: String): Boolean {
        return tryOrNull {
            Json.parseToJsonElement(content).jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull
        } ?: false
    }
}
