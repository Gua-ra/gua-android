/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifRenditions
import io.element.android.features.expressions.api.sticker.StickerContent
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.impl.R
import io.element.android.features.expressions.impl.editor.StickerEditorView
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.IconToggleButton
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import io.element.android.features.expressions.api.R as ApiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressionOverlaysView(
    state: DefaultExpressionPanelState,
    gifImageLoader: ImageLoader,
) {
    val dismiss = { state.panelSink(PanelEvent.DismissOverlay) }
    when (val overlay = state.overlay) {
        is ExpressionOverlay.StickerActions -> ModalBottomSheet(onDismissRequest = dismiss, scrollable = false) {
            StickerActionsContent(
                image = overlay.tile.image,
                actions = stickerActions(overlay.tile, overlay.section, overlay.tile.image.url in state.favouriteStickerUrls, state.panelSink),
            )
        }
        is ExpressionOverlay.ConfirmDelete -> ConfirmationDialog(
            title = stringResource(R.string.gua_stickers_delete_title),
            content = stringResource(R.string.gua_stickers_delete_message),
            submitText = stringResource(CommonStrings.action_delete),
            destructiveSubmit = true,
            onSubmitClick = { state.panelSink(PanelEvent.ConfirmDeleteSticker) },
            onDismiss = dismiss,
        )
        is ExpressionOverlay.GifPreview -> ModalBottomSheet(onDismissRequest = dismiss, scrollable = true) {
            GifPreviewContent(
                item = overlay.item,
                isFavourite = overlay.item.id in state.favouriteGifIds,
                isSending = overlay.isSending,
                imageLoader = gifImageLoader,
                onToggleFavourite = { state.panelSink(PanelEvent.ToggleGifFavourite(overlay.item)) },
                onSend = { caption -> state.panelSink(PanelEvent.SendGif(overlay.item, caption)) },
            )
        }
        is ExpressionOverlay.TimelineSticker -> ModalBottomSheet(onDismissRequest = dismiss, scrollable = false) {
            TimelineStickerSheetContent(
                image = overlay.image,
                packName = overlay.packName,
                isSaved = overlay.image.url in state.savedStickerUrls,
                isFavourite = overlay.image.url in state.favouriteStickerUrls,
                eventSink = state.panelSink,
            )
        }
        is ExpressionOverlay.Editor -> Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            StickerEditorView(editor = overlay.editor, eventSink = state.panelSink)
        }
        null -> Unit
    }
}

@Composable
private fun LargeSticker(image: StickerImage) {
    StickerImageView(
        image = image,
        contentDescription = stringResource(ApiR.string.gua_a11y_sticker, image.body ?: StickerContent.DEFAULT_BODY),
        modifier = Modifier
            .padding(16.dp)
            .size(192.dp),
    )
}

@Composable
internal fun StickerActionsContent(
    image: StickerImage,
    actions: ImmutableList<Pair<String, () -> Unit>>,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LargeSticker(image)
        actions.forEach { (label, action) ->
            ListItem(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@Composable
internal fun TimelineStickerSheetContent(
    image: StickerImage,
    packName: String?,
    isSaved: Boolean,
    isFavourite: Boolean,
    eventSink: (PanelEvent) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LargeSticker(image)
        if (packName != null) {
            Text(
                text = stringResource(R.string.gua_stickers_from_pack, packName),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (!isSaved) {
            ListItem(
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                onClick = { eventSink(PanelEvent.SaveSticker(image)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.gua_stickers_save)) }
        }
        ListItem(
            leadingContent = ListItemContent.Icon(
                IconSource.Vector(if (isFavourite) CompoundIcons.FavouriteSolid() else CompoundIcons.Favourite())
            ),
            onClick = { eventSink(PanelEvent.ToggleStickerFavourite(image)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (isFavourite) R.string.gua_stickers_remove_favourite else R.string.gua_stickers_add_favourite)) }
    }
}

@Composable
internal fun GifPreviewContent(
    item: GifItem,
    isFavourite: Boolean,
    isSending: Boolean,
    imageLoader: ImageLoader,
    onToggleFavourite: () -> Unit,
    onSend: (String) -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    val shown = GifRenditions.forSending(item)?.file ?: item.preview
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shown != null) {
            GifImageView(
                file = shown,
                imageLoader = imageLoader,
                contentDescription = stringResource(ApiR.string.gua_a11y_gif, item.title.ifBlank { GifRenditions.DEFAULT_BODY }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .aspectRatio(shown.width.toFloat() / shown.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = stringResource(R.string.gua_gifs_caption),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconToggleButton(checked = isFavourite, onCheckedChange = { onToggleFavourite() }) {
                Icon(
                    imageVector = if (isFavourite) CompoundIcons.FavouriteSolid() else CompoundIcons.Favourite(),
                    contentDescription = stringResource(if (isFavourite) R.string.gua_stickers_remove_favourite else R.string.gua_stickers_add_favourite),
                )
            }
        }
        Button(
            text = stringResource(CommonStrings.action_send),
            onClick = { onSend(caption) },
            showProgress = isSending,
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun GifPreviewContentPreview() = ElementPreview {
    val context = LocalContext.current
    GifPreviewContent(
        item = aGifItem("hello"),
        isFavourite = true,
        isSending = false,
        imageLoader = remember { ImageLoader(context) },
        onToggleFavourite = {},
        onSend = {},
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineStickerSheetContentPreview() = ElementPreview {
    TimelineStickerSheetContent(
        image = aStickerImage("party"),
        packName = "Family",
        isSaved = false,
        isFavourite = true,
        eventSink = {},
    )
}
