/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

internal interface RoomStateApi {
    @GET("_matrix/client/v3/rooms/{roomId}/state")
    suspend fun state(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
    ): JsonArray
}
