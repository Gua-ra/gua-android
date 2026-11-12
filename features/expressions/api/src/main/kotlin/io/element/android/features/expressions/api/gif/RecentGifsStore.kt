/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import kotlinx.coroutines.flow.Flow

interface RecentGifsStore {
    val recents: Flow<List<String>>

    suspend fun add(id: String)

    suspend fun remove(id: String)

    companion object {
        const val MAX_RECENTS = 30
    }
}
