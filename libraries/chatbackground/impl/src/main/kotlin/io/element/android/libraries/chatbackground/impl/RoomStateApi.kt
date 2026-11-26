/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.impl

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface RoomStateApi {
    // The trailing slash is the empty state key.
    @GET("_matrix/client/v3/rooms/{roomId}/state/{eventType}/")
    suspend fun stateEvent(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: String,
        @Path("eventType") eventType: String,
        @Query("format") format: String,
    ): JsonObject
}
