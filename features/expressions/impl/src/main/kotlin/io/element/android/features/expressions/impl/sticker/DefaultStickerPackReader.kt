/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.sticker.StickerPack
import io.element.android.features.expressions.api.sticker.StickerPackCodec
import io.element.android.features.expressions.api.sticker.StickerPackReader
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the packs from the server: sync does not carry pack state, and the SDK can only list the
 * state the sync carried. Every state key counts, so this reads the whole state.
 */
@ContributesBinding(SessionScope::class)
class DefaultStickerPackReader(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val retrofitFactory: RetrofitFactory,
) : StickerPackReader {
    override suspend fun packs(roomId: RoomId, roomName: String, roomAvatarUrl: String?): Result<List<StickerPack>> = runCatchingExceptions {
        val session = sessionStore.getSession(matrixClient.sessionId.value) ?: error("No stored session")
        // Unlogged, since every request carries the access token.
        val api = retrofitFactory.createUnlogged(session.homeserverUrl).create(RoomStateApi::class.java)
        api.state(authorization = "Bearer ${session.accessToken}", roomId = roomId.value)
            .mapNotNull { it as? JsonObject }
            .mapNotNull { event ->
                val type = event.stringValue("type")?.takeIf { it in StickerPackCodec.EVENT_TYPES } ?: return@mapNotNull null
                val stateKey = event.stringValue("state_key") ?: return@mapNotNull null
                val content = event["content"] as? JsonObject ?: return@mapNotNull null
                StickerPackCodec.decode(type, stateKey, content, roomName, roomAvatarUrl)
            }
            .filter { it.stickers.isNotEmpty() }
            .sortedWith(compareBy({ StickerPackCodec.EVENT_TYPES.indexOf(it.eventType) }, { it.stateKey }))
    }

    private fun JsonObject.stringValue(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
