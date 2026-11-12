/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline

sealed class GifSendException(cause: Throwable?) : Exception(cause) {
    class NoRendition : GifSendException(null)

    class DownloadFailed(cause: Throwable) : GifSendException(cause)

    class SendFailed(cause: Throwable) : GifSendException(cause)
}

/** Sends a GIF to the current chat as an animated image, through the send queue. */
interface GifSender {
    suspend fun send(
        item: GifItem,
        caption: String?,
        inReplyToEventId: EventId?,
        timelineMode: Timeline.Mode,
    ): Result<Unit>
}
