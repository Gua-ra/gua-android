/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.messagemenu

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.text.TextRange
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.R
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class MessageTextSelectionSheetTest : RobolectricTest() {
    private val text = "Me mudei pra Floripa ano passado, tô morando de frente pro mar agora"

    @Test
    fun `the sheet shows the message text, all of it selected`() = runAndroidComposeUiTest {
        setSafeContent {
            MessageTextSelectionSheet(text = text, onDismiss = EnsureNeverCalled())
        }

        onNodeWithText(requireNotNull(activity).getString(R.string.gua_message_text_selection_title)).assertIsDisplayed()
        onNode(isEditableText)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, text.length)))
            .assertIsFocused()
    }

    @Test
    fun `Done closes the sheet`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setSafeContent {
                MessageTextSelectionSheet(text = text, onDismiss = callback)
            }
            clickOn(CommonStrings.action_done)
            mainClock.advanceTimeBy(1_000)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `in a short window the sheet opens fully, with room below the text for the selection handles`() = runAndroidComposeUiTest {
        setSafeContent {
            MessageTextSelectionSheet(text = text, onDismiss = EnsureNeverCalled())
        }
        mainClock.advanceTimeBy(1_000)

        val field = onNode(isEditableText).fetchSemanticsNode()
        val windowHeight = field.layoutInfo.coordinates.findRootCoordinates().size.height
        with(density) {
            assertThat(field.boundsInRoot.bottom + SELECTION_HANDLE_SPACE.toPx()).isAtMost(windowHeight + 1f)
        }
    }

    @Test
    fun `nothing shows without text`() = runAndroidComposeUiTest {
        setSafeContent {
            MessageTextSelectionSheet(text = null, onDismiss = EnsureNeverCalled())
        }

        onNode(isEditableText).assertDoesNotExist()
    }

    @Test
    fun `the system edit menu shows for the selection, and Copy copies it`() = runAndroidComposeUiTest {
        val textToolbar = FakeTextToolbar()
        setSafeContent {
            CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                SelectableText(text = text)
            }
        }
        mainClock.advanceTimeBy(1_000)

        assertThat(textToolbar.status).isEqualTo(TextToolbarStatus.Shown)
        requireNotNull(textToolbar.onCopyRequested).invoke()
        mainClock.advanceTimeBy(1_000)

        assertThat(textToolbar.status).isEqualTo(TextToolbarStatus.Hidden)
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()).isEqualTo(text)
    }

    @Test
    fun `the system edit menu sits on the selected text, with room above it for the menu`() = runAndroidComposeUiTest {
        val textToolbar = FakeTextToolbar()
        setSafeContent {
            CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                Box(modifier = Modifier.testTag(AREA_TAG)) {
                    SelectableText(text = text)
                }
            }
        }
        mainClock.advanceTimeBy(1_000)

        val area = onNodeWithTag(AREA_TAG).getBoundsInRoot()
        val field = onNode(isEditableText).getBoundsInRoot()
        val rect = requireNotNull(textToolbar.rect)
        with(density) {
            assertThat((field.top - area.top).value).isAtLeast(EDIT_MENU_SPACE.value - 0.5f)
            assertThat(rect.top.toDp().value).isWithin(0.5f).of(field.top.value)
            assertThat(rect.bottom.toDp().value).isAtMost(field.bottom.value + 0.5f)
        }
    }

    @Test
    fun `tapping the selection shows the system edit menu again and keeps the selection`() = runAndroidComposeUiTest {
        val textToolbar = FakeTextToolbar()
        setSafeContent {
            CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                SelectableText(text = text)
            }
        }
        mainClock.advanceTimeBy(1_000)
        textToolbar.hide()

        onNode(isEditableText).performClick()
        mainClock.advanceTimeBy(1_000)

        assertThat(textToolbar.status).isEqualTo(TextToolbarStatus.Shown)
        onNode(isEditableText).assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, text.length)))
    }
}

private val isEditableText = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
private const val AREA_TAG = "area"

private class FakeTextToolbar : TextToolbar {
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    var onCopyRequested: (() -> Unit)? = null
        private set

    var rect: Rect? = null
        private set

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.onCopyRequested = onCopyRequested
        this.rect = rect
        status = TextToolbarStatus.Shown
    }
}
