/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.ui.media.contentvalidation.NoopContentValidationState
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically

@PreviewsDayNight
@Composable
internal fun TimelineItemImageViewGifStillPreview() = ElementPreview {
    CompositionLocalProvider(LocalPlayGifsAutomatically provides false) {
        TimelineItemImageView(
            content = aTimelineItemImageContent(filename = "A GIF.gif").copy(
                mimeType = MimeTypes.Gif,
                isAnimated = true,
                fileExtension = "gif",
            ),
            hideMediaContent = false,
            onShowContentClick = {},
            onContentClick = {},
            onLongClick = {},
            contentValidationState = NoopContentValidationState(),
        )
    }
}
