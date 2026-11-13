/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.keyboard

import dev.zacsweers.metro.Inject
import io.element.android.features.expressions.api.gif.GifSender
import io.element.android.features.expressions.api.panel.ExpressionComposer
import io.element.android.features.expressions.api.panel.KeyboardContent
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerSender
import io.element.android.features.expressions.impl.sticker.StickerImageEncoder
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.flatMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

@Inject
class KeyboardStickers(
    private val imageFiles: ImageFiles,
    private val stickerSender: StickerSender,
    private val gifSender: GifSender,
    private val encoder: StickerImageEncoder,
    private val dispatchers: CoroutineDispatchers,
) {
    data class Received(val file: File, val mimeType: String, val route: KeyboardRoute)

    sealed interface Outcome {
        data object NotOurs : Outcome

        data object SentGif : Outcome

        data class SentSticker(val image: StickerImage) : Outcome

        data class Failed(val error: Throwable) : Outcome
    }

    /** Runs inside the receive callback, copying the content while its uri is still readable. */
    fun take(content: KeyboardContent): Received? {
        val mimeType = content.mimeType ?: imageFiles.mimeType(content.uri) ?: return null
        val route = KeyboardContentRules.route(mimeType, content.isFromInputMethod)
        if (route == KeyboardRoute.Unchanged) return null
        val file = imageFiles.copy(content.uri) ?: return null
        return Received(file, mimeType, route)
    }

    suspend fun send(received: Received, composer: ExpressionComposer): Outcome {
        if (received.route == KeyboardRoute.Gif) {
            return gifSender.sendFile(received.file, composer.inReplyToEventId, composer.timelineMode).fold(
                onSuccess = {
                    composer.onSent()
                    Outcome.SentGif
                },
                onFailure = { Outcome.Failed(it) },
            )
        }
        return try {
            val stickerFile = withContext(dispatchers.io) { stickerFile(received) } ?: return Outcome.NotOurs
            stickerSender.upload(stickerFile)
                .flatMap { image -> stickerSender.send(image, composer.stickerRelation()).map { image } }
                .fold(
                    onSuccess = { image ->
                        composer.onSent()
                        Outcome.SentSticker(image)
                    },
                    onFailure = { Outcome.Failed(it) },
                )
        } finally {
            withContext(NonCancellable + dispatchers.io) { received.file.delete() }
        }
    }

    private fun stickerFile(received: Received): StickerFile? {
        val facts = imageFiles.facts(received.file, received.mimeType) ?: return null
        if (received.route == KeyboardRoute.StickerIfTransparent && !KeyboardContentRules.isSticker(facts)) return null
        return if (KeyboardContentRules.needsReencoding(facts, received.file.length())) {
            imageFiles.decode(received.file, KeyboardContentRules.MAX_KEYBOARD_STICKER_SIDE)?.let { encoder.fit(it, StickerContent.DEFAULT_BODY) }
        } else {
            StickerFile(
                bytes = received.file.readBytes(),
                mimetype = KeyboardContentRules.uploadMimeType(received.mimeType),
                width = facts.width,
                height = facts.height,
                body = StickerContent.DEFAULT_BODY,
                isAnimated = facts.isAnimated,
            )
        }
    }
}
