/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.content.ClipData
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHOW_TOOLBAR_DELAY_MILLIS = 400L
internal val EDIT_MENU_SPACE = 56.dp
internal val SELECTION_HANDLE_SPACE = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTextSelectionSheet(
    text: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = true,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.gua_message_text_selection_title),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            TextButton(
                text = stringResource(CommonStrings.action_done),
                onClick = { sheetState.hide(coroutineScope) { onDismiss() } },
            )
        }
        SelectableText(text = text)
    }
}

@Composable
internal fun SelectableText(text: String) {
    val textFieldState = rememberTextFieldState(initialText = text, initialSelection = TextRange(0, text.length))
    val focusRequester = remember { FocusRequester() }
    val textToolbar = LocalTextToolbar.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayout by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }

    fun showToolbar() {
        val fieldCoordinates = coordinates?.takeIf { it.isAttached } ?: return
        val range = textFieldState.selection
        if (range.collapsed) return
        val visibleBounds = fieldCoordinates.boundsInRoot()
        val selectionBounds = textLayout?.invoke()
            ?.getPathForRange(range.min, range.max)
            ?.getBounds()
            ?.translate(fieldCoordinates.positionInRoot())
            ?.intersect(visibleBounds)
            ?.takeUnless { it.isEmpty }
        textToolbar.showMenu(
            rect = selectionBounds ?: visibleBounds,
            onCopyRequested = {
                val selection = textFieldState.selection
                val selectedText = textFieldState.text.substring(selection.min, selection.max)
                coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, selectedText))) }
                textToolbar.hide()
            },
            onSelectAllRequested = {
                textFieldState.edit { selection = TextRange(0, length) }
                showToolbar()
            },
        )
    }

    BasicTextField(
        state = textFieldState,
        readOnly = true,
        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
        cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
        onTextLayout = { getResult -> textLayout = getResult },
        modifier = Modifier
            .fillMaxWidth()
            // The platform edit menu floats 56 dp above the selection, and the selection handles hang 25 dp below it.
            .padding(start = 16.dp, top = EDIT_MENU_SPACE, end = 16.dp, bottom = SELECTION_HANDLE_SPACE)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(Unit) { detectSelectionTaps(onTap = ::showToolbar, hasSelection = { !textFieldState.selection.collapsed }) }
            .focusRequester(focusRequester),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // The field reaches its final place only once the sheet has settled.
        delay(SHOW_TOOLBAR_DELAY_MILLIS)
        showToolbar()
    }
    DisposableEffect(Unit) {
        onDispose { textToolbar.hide() }
    }
}

// A quick tap on the selection shows the edit menu instead of moving the cursor; long presses still reach the field.
private suspend fun PointerInputScope.detectSelectionTaps(onTap: () -> Unit, hasSelection: () -> Boolean) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        val moved = (up.position - down.position).getDistance() > viewConfiguration.touchSlop
        if (!moved && hasSelection()) {
            up.consume()
            onTap()
        }
    }
}
