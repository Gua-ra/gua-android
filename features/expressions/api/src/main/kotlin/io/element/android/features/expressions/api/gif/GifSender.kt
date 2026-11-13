/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.gif

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import java.io.File

sealed class GifSendException(cause: Throwable?) : Exception(cause) {
    class NoRendition : GifSendException(null)

    class DownloadFailed(cause: Throwable) : GifSendException(cause)

    class UnreadableFile(cause: Throwable) : GifSendException(cause)

    class SendFailed(cause: Throwable) : GifSendException(cause)
}

interface GifSender {
    suspend fun send(
        item: GifItem,
        caption: String?,
        inReplyToEventId: EventId?,
        timelineMode: Timeline.Mode,
    ): Result<Unit>

    /** Sends a GIF file a keyboard inserted; the file is not touched and is deleted afterwards. */
    suspend fun sendFile(
        file: File,
        inReplyToEventId: EventId?,
        timelineMode: Timeline.Mode,
    ): Result<Unit>
}
