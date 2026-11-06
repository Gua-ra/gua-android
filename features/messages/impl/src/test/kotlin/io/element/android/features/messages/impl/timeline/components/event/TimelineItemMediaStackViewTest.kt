/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.protection.ProtectionState
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class TimelineItemMediaStackViewTest : RobolectricTest() {
    private val content = aMediaStackEvent(
        isMine = false,
        members = listOf(aTimelineItemImageContent(caption = "Beach day")) + List(2) { aTimelineItemImageContent() },
    ).content as TimelineItemMediaStackContent

    @Test
    fun `the stack is named by its header and caption, and its position is its state`() = runAndroidComposeUiTest<ComponentActivity> {
        setStack()

        val header = activity!!.resources.getQuantityString(R.plurals.gua_media_stack_photos, 3, 3)
        val position = activity!!.getString(R.string.gua_media_stack_a11y_photo_position, 1, 3)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("$header, Beach day")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, position))
    }

    @Test
    fun `a stack whose media are hidden ends with Show, which activating it does`() = runAndroidComposeUiTest<ComponentActivity> {
        setStack(timelineProtectionState = aTimelineProtectionState(protectionState = ProtectionState.RenderOnly(persistentSetOf())))

        val position = activity!!.getString(R.string.gua_media_stack_a11y_photo_position, 1, 3)
        val show = activity!!.getString(CommonStrings.action_show)
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$position, $show")).assertExists()
    }

    @Test
    fun `the header takes taps across a 48 dp touch target centred on it`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<TimelineEvent.TimelineItemEvent>()
        setStack(eventSink = eventsRecorder)
        val header = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        // Compose widens a clickable smaller than the minimum touch target evenly around it, without moving anything.
        val expansion = (with(density) { 48.dp.toPx() } - header.fetchSemanticsNode().boundsInRoot.height) / 2
        waitForIdle()
        eventsRecorder.clear()

        header.performTouchInput { click(Offset(centerX, -expansion + 1f)) }
        eventsRecorder.assertSingle(TimelineEvent.OpenMediaStackGrid(content))

        eventsRecorder.clear()
        header.performTouchInput { click(Offset(centerX, -expansion - 2f)) }
        eventsRecorder.assertEmpty()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setStack(
        timelineProtectionState: TimelineProtectionState = aTimelineProtectionState(),
        eventSink: (TimelineEvent.TimelineItemEvent) -> Unit = {},
    ) {
        setContent {
            // The caption's rich text view needs a native library the JVM tests lack.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(modifier = Modifier.padding(top = 40.dp)) {
                    TimelineItemMediaStackView(
                        content = content,
                        isMine = false,
                        gesturesEnabled = true,
                        timelineProtectionState = timelineProtectionState,
                        eventSink = eventSink,
                        onActivate = {},
                        onLinkClick = {},
                        onLinkLongClick = {},
                        timestamp = {},
                    )
                }
            }
        }
    }
}
