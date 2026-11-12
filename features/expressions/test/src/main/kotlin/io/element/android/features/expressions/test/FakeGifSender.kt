/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifSender
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.tests.testutils.lambda.lambdaError

class FakeGifSender(
    private val sendResult: (GifItem, String?, EventId?, Timeline.Mode) -> Result<Unit> = { _, _, _, _ -> lambdaError() },
) : GifSender {
    override suspend fun send(item: GifItem, caption: String?, inReplyToEventId: EventId?, timelineMode: Timeline.Mode): Result<Unit> =
        sendResult(item, caption, inReplyToEventId, timelineMode)
}
