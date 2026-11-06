/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.components.event.MediaStackOverlayColor
import io.element.android.features.messages.impl.timeline.components.event.MediaStackScanFailure
import io.element.android.features.messages.impl.timeline.components.event.mediaStackHeaderText
import io.element.android.features.messages.impl.timeline.components.event.mediaStackPositionText
import io.element.android.features.messages.impl.timeline.components.event.mediaStackStill
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.util.handleAsyncImageStateChange
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.components.A_BLUR_HASH
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationState
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.matrix.ui.media.contentvalidation.rememberEventContentValidationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val WIDE_LAYOUT_MIN_WIDTH = 600.dp

@Composable
fun MediaStackGridView(
    kind: TimelineItemMediaStackContent.Kind,
    tiles: ImmutableList<MediaStackGridTile>,
    onBackClick: () -> Unit,
    onTileClick: (Int) -> Unit,
    onValidate: (List<MediaSource>, ContentValidationState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = mediaStackHeaderText(kind, tiles.size),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (maxWidth > WIDE_LAYOUT_MIN_WIDTH) 4 else 3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(tiles) { index, tile ->
                    MediaStackGridTileView(
                        tile = tile,
                        description = mediaStackPositionText(index, tiles.size, tile.videoDuration),
                        onClick = { onTileClick(index) },
                        onValidate = onValidate,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaStackGridTileView(
    tile: MediaStackGridTile,
    description: String,
    onClick: () -> Unit,
    onValidate: (List<MediaSource>, ContentValidationState) -> Unit,
) {
    val validationState = rememberEventContentValidationState(eventId = tile.eventId, needsValidation = true)
    val validation by validationState.collectOverallState()
    val latestOnValidate by rememberUpdatedState(onValidate)
    LaunchedEffect(tile.eventId) {
        if (tile.eventId != null) latestOnValidate(tile.mediaSources, validationState)
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .blurHashBackground(tile.blurhash, alpha = 0.9f)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        when {
            validation.hasError() -> MediaStackScanFailure()
            !validation.isValidated() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> {
                val still = tile.stillSource?.let(::mediaStackStill)
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = still,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    onState = { state ->
                        val url = still?.source?.safeUrl ?: return@AsyncImage
                        handleAsyncImageStateChange(
                            state = state,
                            onLoaded = {},
                            updateContentValidationState = { validationState.update(url, it) },
                        )
                    },
                )
                tile.videoDuration?.let { duration -> VideoTileOverlay(duration) }
            }
        }
    }
}

@Composable
private fun BoxScope.VideoTileOverlay(duration: String) {
    Icon(
        imageVector = CompoundIcons.PlaySolid(),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .align(Alignment.Center)
            .background(MediaStackOverlayColor, CircleShape)
            .padding(6.dp)
            .size(20.dp),
    )
    Text(
        text = duration,
        style = ElementTheme.typography.fontBodyXsMedium.copy(fontFeatureSettings = "tnum"),
        color = Color.White,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .background(MediaStackOverlayColor, CircleShape)
            .padding(horizontal = 4.dp),
    )
}

@PreviewsDayNight
@Composable
internal fun MediaStackGridViewPreview() = ElementPreview {
    MediaStackGridView(
        kind = TimelineItemMediaStackContent.Kind.Items,
        tiles = persistentListOf(
            aMediaStackGridTile(),
            aMediaStackGridTile(videoDuration = "0:42"),
            aMediaStackGridTile(),
            aMediaStackGridTile(),
            aMediaStackGridTile(videoDuration = "1:05"),
        ),
        onBackClick = {},
        onTileClick = {},
        onValidate = { _, _ -> },
    )
}

private fun aMediaStackGridTile(videoDuration: String? = null) = MediaStackGridTile(
    eventId = null,
    stillSource = null,
    mediaSources = emptyList(),
    blurhash = A_BLUR_HASH,
    videoDuration = videoDuration,
)
