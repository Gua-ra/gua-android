/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContentPreviewParam
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.features.messages.impl.timeline.util.handleAsyncImageStateChange
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationState
import io.element.android.libraries.matrix.ui.media.contentvalidation.NoopContentValidationState
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.libraries.matrixmedia.api.firstFrameOnly
import io.element.android.libraries.matrixmedia.api.shouldAnimateImages
import io.element.android.libraries.ui.strings.CommonStrings

private val STICKER_MAX_SIZE = 160.dp

/** A sticker shows at its info size, else at the largest size, fitted within 160×160. */
internal fun stickerDisplaySize(width: Int?, height: Int?): DpSize {
    if (width == null || height == null || minOf(width, height) <= 0) return DpSize(STICKER_MAX_SIZE, STICKER_MAX_SIZE)
    val scale = minOf(1f, STICKER_MAX_SIZE.value / maxOf(width, height))
    return DpSize((width * scale).dp, (height * scale).dp)
}

@Composable
fun TimelineItemStickerView(
    content: TimelineItemStickerContent,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowClick: () -> Unit,
    contentValidationState: ContentValidationState,
    modifier: Modifier = Modifier,
) {
    val description = content.bestDescription.takeIf { it.isNotEmpty() } ?: stringResource(CommonStrings.common_image)

    val eventContentValidation by contentValidationState.collectOverallState()
    val isContentBeingValidated = !eventContentValidation.isValidated()
    Column(
        modifier = modifier.semantics { contentDescription = description }.wrapContentWidth(Alignment.CenterHorizontally),
    ) {
        var isThumbnailLoaded by rememberSaveable { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(if (hideMediaContent) DpSize(STICKER_MAX_SIZE, STICKER_MAX_SIZE) else stickerDisplaySize(content.width, content.height))
                .then(if (isThumbnailLoaded) Modifier else Modifier.blurHashBackground(content.blurhash, alpha = 0.9f)),
        ) {
            ProtectedView(
                hideContent = hideMediaContent,
                onShowClick = onShowClick,
            ) {
                val context = LocalContext.current
                val shouldAnimate = context.shouldAnimateImages()
                val model = remember(content.preferredMediaSource, content.filename, content.mimeType, shouldAnimate) {
                    val request = MediaRequestData(
                        source = content.preferredMediaSource,
                        kind = MediaRequestData.Kind.File(
                            fileName = content.filename,
                            mimeType = content.mimeType,
                        ),
                    )
                    if (shouldAnimate) request else ImageRequest.Builder(context).data(request).firstFrameOnly().build()
                }
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (onContentClick != null) {
                                Modifier
                                    .combinedClickable(
                                        onClick = onContentClick,
                                        onLongClick = onLongClick,
                                        onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                                    )
                                    .onKeyboardContextMenuAction(onLongClick)
                            } else {
                                Modifier
                            }
                        ),
                    model = model,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    contentDescription = description,
                    onState = { state ->
                        val url = content.preferredMediaSource?.safeUrl
                        if (url != null) {
                            handleAsyncImageStateChange(
                                state = state,
                                onLoaded = { isThumbnailLoaded = true },
                                updateContentValidationState = { contentValidationState.update(url, it) },
                            )
                        }
                    },
                )
            }

            if (isContentBeingValidated) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemStickerViewPreview(@PreviewParameter(
    TimelineItemStickerContentPreviewParam::class
) content: TimelineItemStickerContent) = ElementPreview {
    TimelineItemStickerView(
        content = content,
        hideMediaContent = false,
        onContentClick = {},
        onLongClick = {},
        onShowClick = {},
        contentValidationState = NoopContentValidationState(),
    )
}
