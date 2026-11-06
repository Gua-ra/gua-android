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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.event.mediaStackHeaderText
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.components.A_BLUR_HASH
import io.element.android.libraries.matrixmedia.api.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrixmedia.api.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private val TileOverlayColor = Color.Black.copy(alpha = 0.45f)

@Composable
fun MediaStackGridView(
    kind: TimelineItemMediaStackContent.Kind,
    tiles: ImmutableList<MediaStackGridTile>,
    onBackClick: () -> Unit,
    onTileClick: (Int) -> Unit,
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
                    val description = if (tile.videoDuration != null) {
                        stringResource(R.string.gua_media_stack_a11y_video_position, index + 1, tiles.size, tile.videoDuration)
                    } else {
                        stringResource(R.string.gua_media_stack_a11y_photo_position, index + 1, tiles.size)
                    }
                    MediaStackGridTileView(tile = tile, description = description, onClick = { onTileClick(index) })
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
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .blurHashBackground(tile.blurhash, alpha = 0.9f)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        tile.thumbnailSource?.let { source ->
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = MediaRequestData(source, MediaRequestData.Kind.Thumbnail(MAX_THUMBNAIL_WIDTH, MAX_THUMBNAIL_HEIGHT)),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        }
        tile.videoDuration?.let { duration ->
            Icon(
                imageVector = CompoundIcons.PlaySolid(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(TileOverlayColor, CircleShape)
                    .padding(6.dp)
                    .size(20.dp),
            )
            Text(
                text = duration,
                style = ElementTheme.typography.fontBodyXsMedium.copy(fontFamily = FontFamily.Monospace),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(TileOverlayColor, CircleShape)
                    .padding(horizontal = 4.dp),
            )
        }
    }
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
    )
}

private fun aMediaStackGridTile(videoDuration: String? = null) = MediaStackGridTile(
    thumbnailSource = null,
    blurhash = A_BLUR_HASH,
    videoDuration = videoDuration,
)
