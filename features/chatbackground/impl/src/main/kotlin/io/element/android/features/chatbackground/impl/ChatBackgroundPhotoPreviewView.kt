/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.designsystem.chatbackground.LocalChatBackgroundActive
import io.element.android.libraries.designsystem.chatbackground.legibilityCapsule
import io.element.android.libraries.designsystem.components.blurhash.BlurHashImage
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.chatBackgroundPhotoAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val DIM_STEP = 0.05
private const val NUDGE = 0.1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatBackgroundPhotoPreviewView(
    draft: ChatBackgroundPhotoDraft,
    canSet: Boolean,
    eventSink: (ChatBackgroundEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_background_screen_title),
                navigationIcon = { BackButton(onClick = { eventSink(ChatBackgroundEvent.DiscardPhoto) }) },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        index = 0,
                        count = 2,
                        selected = !draft.isDarkPreview,
                        onClick = { eventSink(ChatBackgroundEvent.SetDarkPreview(false)) },
                        text = stringResource(R.string.gua_chat_background_preview_light),
                    )
                    SegmentedButton(
                        index = 1,
                        count = 2,
                        selected = draft.isDarkPreview,
                        onClick = { eventSink(ChatBackgroundEvent.SetDarkPreview(true)) },
                        text = stringResource(R.string.gua_chat_background_preview_dark),
                    )
                }
                Text(
                    text = stringResource(R.string.gua_chat_background_dim),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Slider(
                    value = draft.dim.toFloat(),
                    onValueChange = { eventSink(ChatBackgroundEvent.SetPhotoDim((it / DIM_STEP).roundToInt() * DIM_STEP)) },
                    valueRange = 0f..ChatBackground.Photo.MAX_DIM.toFloat(),
                    steps = (ChatBackground.Photo.MAX_DIM / DIM_STEP).roundToInt() - 1,
                )
                Text(
                    text = stringResource(R.string.gua_chat_background_photo_note),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                Button(
                    text = stringResource(R.string.gua_chat_background_action_set),
                    onClick = { eventSink(ChatBackgroundEvent.Apply) },
                    enabled = canSet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        ElementTheme(theme = if (draft.isDarkPreview) Theme.Dark else Theme.Light, applySystemBarsUpdate = false) {
            PhotoFrame(
                draft = draft,
                eventSink = eventSink,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PhotoFrame(
    draft: ChatBackgroundPhotoDraft,
    eventSink: (ChatBackgroundEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, draft.upload) {
        value = withContext(Dispatchers.Default) {
            tryOrNull { BitmapFactory.decodeByteArray(draft.upload.data, 0, draft.upload.data.size)?.asImageBitmap() }
        }
    }
    val currentDraft by rememberUpdatedState(draft)
    val moveLeft = stringResource(R.string.gua_chat_background_a11y_move_left)
    val moveRight = stringResource(R.string.gua_chat_background_a11y_move_right)
    val moveUp = stringResource(R.string.gua_chat_background_a11y_move_up)
    val moveDown = stringResource(R.string.gua_chat_background_a11y_move_down)
    fun nudge(dx: Double, dy: Double): Boolean {
        eventSink(ChatBackgroundEvent.SetPhotoAlign(currentDraft.alignX + dx, currentDraft.alignY + dy))
        return true
    }
    BoxWithConstraints(
        modifier = modifier
            .background(ElementTheme.colors.bgCanvasDefault)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(moveLeft) { nudge(-NUDGE, 0.0) },
                    CustomAccessibilityAction(moveRight) { nudge(NUDGE, 0.0) },
                    CustomAccessibilityAction(moveUp) { nudge(0.0, -NUDGE) },
                    CustomAccessibilityAction(moveDown) { nudge(0.0, NUDGE) },
                )
            },
    ) {
        val density = LocalDensity.current
        val frameWidth = with(density) { maxWidth.toPx() }
        val frameHeight = with(density) { maxHeight.toPx() }
        val photoWidth = draft.upload.width.toFloat()
        val photoHeight = draft.upload.height.toFloat()
        val scale = max(frameWidth / photoWidth, frameHeight / photoHeight)
        val overflowX = photoWidth * scale - frameWidth
        val overflowY = photoHeight * scale - frameHeight
        val dragModifier = Modifier.pointerInput(overflowX, overflowY) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                // Dragging right moves the photo right, which is a smaller align: the offset is -overflow × align.
                val x = if (overflowX > 0f) currentDraft.alignX - dragAmount.x / overflowX else currentDraft.alignX
                val y = if (overflowY > 0f) currentDraft.alignY - dragAmount.y / overflowY else currentDraft.alignY
                eventSink(ChatBackgroundEvent.SetPhotoAlign(x, y))
            }
        }
        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            val alignment = chatBackgroundPhotoAlignment(draft.alignX, draft.alignY)
            image?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = alignment)
            } ?: BlurHashImage(blurHash = draft.upload.blurhash, contentScale = ContentScale.Crop)
            if (draft.isDarkPreview && draft.dim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = draft.dim.toFloat())))
            }
            ChatBackgroundSampleMessages(isOnBackground = true)
            CompositionLocalProvider(LocalChatBackgroundActive provides true) {
                Text(
                    text = stringResource(R.string.gua_chat_background_position_hint),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .legibilityCapsule(),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChatBackgroundPhotoPreviewViewPreview(@PreviewParameter(ChatBackgroundPhotoDraftPreviewParam::class) draft: ChatBackgroundPhotoDraft) =
    ElementPreview {
        ChatBackgroundPhotoPreviewView(
            draft = draft,
            canSet = true,
            eventSink = {},
        )
    }
