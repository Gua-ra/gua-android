/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

class GifsUnavailableException : Exception("This build has no GIF provider key")

/** Results keep the provider's order. */
interface GifProvider {
    val id: String

    val isAvailable: Boolean

    suspend fun search(query: String, page: Int): Result<GifPage>

    suspend fun trending(page: Int): Result<GifPage>

    suspend fun items(ids: List<String>): Result<List<GifItem>>
}
