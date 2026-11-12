/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.expressions.impl.R
import io.element.android.features.expressions.impl.panel.PanelEvent
import io.element.android.features.expressions.impl.panel.StickerEditorState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Switch
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.roundToInt

private val TEXT_OUTLINE_COLOR = Color(0xE6000000)

@Composable
internal fun StickerEditorView(
    editor: StickerEditorState,
    eventSink: (PanelEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var frame by remember { mutableStateOf(EditorFrame.Initial) }
    Surface(modifier = modifier.fillMaxSize(), color = ElementTheme.colors.bgCanvasDefault) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.gua_stickers_create), style = ElementTheme.typography.fontHeadingSmMedium)
            EditorCanvas(editor = editor, frame = frame, onFrameChange = { frame = it })
            if (editor.cutOut != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = editor.isCutOutOn,
                            role = Role.Switch,
                            onValueChange = { eventSink(PanelEvent.SetCutOut(it)) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.gua_stickers_editor_cut_out), modifier = Modifier.weight(1f))
                    Switch(checked = editor.isCutOutOn, onCheckedChange = null)
                }
            }
            TextField(
                value = editor.text,
                onValueChange = { eventSink(PanelEvent.UpdateStickerText(it)) },
                label = stringResource(R.string.gua_stickers_editor_text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val canSave = editor.shownPicture != null && !editor.isSaving
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(text = stringResource(CommonStrings.action_cancel), onClick = { eventSink(PanelEvent.DismissOverlay) })
                OutlinedButton(
                    text = stringResource(CommonStrings.action_save),
                    onClick = { eventSink(PanelEvent.SaveCreatedSticker(frame, send = false)) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = stringResource(CommonStrings.action_send),
                    onClick = { eventSink(PanelEvent.SaveCreatedSticker(frame, send = true)) },
                    enabled = canSave,
                    showProgress = editor.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
            if (editor.showsNote) {
                Text(
                    text = stringResource(R.string.gua_stickers_editor_note),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EditorCanvas(
    editor: StickerEditorState,
    frame: EditorFrame,
    onFrameChange: (EditorFrame) -> Unit,
) {
    val density = LocalDensity.current
    val currentFrame by rememberUpdatedState(frame)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds()
            .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(16.dp)),
    ) {
        val side = constraints.maxWidth.toFloat()
        val picture = editor.shownPicture
        if (picture == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val bitmap = remember(picture) { picture.asImageBitmap() }
            val destination = EditorGeometry.destination(picture.width, picture.height, side, frame)
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(destination.left.roundToInt(), destination.top.roundToInt()) }
                    .size(with(density) { destination.width.toDp() }, with(density) { destination.height.toDp() }),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(side) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onFrameChange(EditorGeometry.transformed(currentFrame, zoom, pan.x, pan.y, side))
                    }
                },
        )
        if (editor.text.isNotBlank()) {
            EditorText(text = editor.text.trim(), side = side, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Matches the text the 512 px render draws, scaled to the preview. */
@Composable
private fun EditorText(text: String, side: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scale = side / EditorGeometry.CANVAS_SIZE
    val fullSize = with(density) { (EditorGeometry.TEXT_SIZE * scale).toSp() }
    val base = TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = fullSize)
    val measured = measurer.measure(text, base, softWrap = false).size.width.toFloat()
    val style = base.copy(fontSize = with(density) { EditorGeometry.textSize(measured, side).toSp() })
    val bottom = with(density) { (EditorGeometry.TEXT_MARGIN * scale).toDp() }
    Box(modifier = modifier.padding(bottom = bottom)) {
        Text(
            text = text,
            style = style.copy(color = TEXT_OUTLINE_COLOR, drawStyle = Stroke(width = EditorGeometry.TEXT_STROKE_WIDTH * scale)),
            maxLines = 1,
            softWrap = false,
        )
        Text(text = text, style = style.copy(color = Color.White), maxLines = 1, softWrap = false)
    }
}

@PreviewsDayNight
@Composable
internal fun StickerEditorViewPreview(@PreviewParameter(StickerEditorStatePreviewParam::class) editor: StickerEditorState) = ElementPreview {
    StickerEditorView(editor = editor, eventSink = {})
}
