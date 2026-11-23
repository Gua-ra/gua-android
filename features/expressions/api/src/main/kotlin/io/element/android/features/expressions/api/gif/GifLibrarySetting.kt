/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import kotlinx.coroutines.flow.StateFlow

interface GifLibrarySetting {
    val library: StateFlow<GifLibrary>

    fun preload()

    suspend fun favourite(id: String): Result<Unit>

    suspend fun unfavourite(id: String): Result<Unit>
}
