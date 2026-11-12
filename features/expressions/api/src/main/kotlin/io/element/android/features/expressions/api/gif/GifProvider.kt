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
    /** The provider name stored with favourites. */
    val id: String

    /** False when this build cannot reach the provider; every request then fails with [GifsUnavailableException]. */
    val isAvailable: Boolean

    /** [page] starts at 1. */
    suspend fun search(query: String, page: Int): Result<GifPage>

    suspend fun trending(page: Int): Result<GifPage>

    /** Items in the order of [ids]; ids the provider no longer has are left out. */
    suspend fun items(ids: List<String>): Result<List<GifItem>>
}
