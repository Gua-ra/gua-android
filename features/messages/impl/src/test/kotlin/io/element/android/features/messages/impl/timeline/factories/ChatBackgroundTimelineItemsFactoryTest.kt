/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemsFactory
import io.element.android.features.messages.impl.timeline.groups.canBeGrouped
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemChatBackgroundContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateEventContent
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatBackgroundTimelineItemsFactoryTest {
    private val background = StateContent("", OtherState.ChatBackground("""{"v":1,"kind":"preset","preset":"mint"}"""))
    private val otherCustomState = StateContent("", OtherState.Custom("org.example.custom"))
    private val topicChange = StateContent("", OtherState.RoomTopic("Topic"))

    @Test
    fun `the background notice shows in 1-1 chats, other state there does not`() = runTest {
        val contents = render(isDirectOneToOneRoom = true, background, topicChange)

        assertThat(contents).hasSize(1)
        assertThat(contents.single()).isInstanceOf(TimelineItemChatBackgroundContent::class.java)
    }

    @Test
    fun `other custom state is never shown`() = runTest {
        val contents = render(isDirectOneToOneRoom = false, otherCustomState, background, topicChange)

        assertThat(contents.map { it::class }).containsExactly(TimelineItemChatBackgroundContent::class, TimelineItemStateEventContent::class)
    }

    @Test
    fun `the background notice is never collapsed with other changes`() = runTest {
        val factory = aTimelineItemsFactory(TimelineItemsFactoryConfig(computeReadReceipts = false, computeReactions = false, isDirectOneToOneRoom = false))
        factory.timelineItems.test {
            factory.replaceWith(timelineItems = items(topicChange, topicChange, background, background), roomMembers = emptyList(), renderReadReceipts = false)
            val rendered = awaitItem()
            val notices = rendered.filterIsInstance<TimelineItem.Event>().filter { it.content is TimelineItemChatBackgroundContent }
            assertThat(notices).hasSize(2)
            assertThat(notices.none { it.canBeGrouped() }).isTrue()
            assertThat(rendered.filterIsInstance<TimelineItem.GroupedEvents>().single().events.map { it.content })
                .containsExactly(TimelineItemStateEventContent(""), TimelineItemStateEventContent(""))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun TestScope.render(isDirectOneToOneRoom: Boolean, vararg contents: EventContent): List<Any> {
        val factory = aTimelineItemsFactory(
            TimelineItemsFactoryConfig(computeReadReceipts = false, computeReactions = false, isDirectOneToOneRoom = isDirectOneToOneRoom)
        )
        var rendered: List<Any> = emptyList()
        factory.timelineItems.test {
            factory.replaceWith(timelineItems = items(*contents), roomMembers = emptyList(), renderReadReceipts = false)
            rendered = awaitItem().flatMap { item ->
                when (item) {
                    is TimelineItem.Event -> listOf(item.content)
                    is TimelineItem.GroupedEvents -> item.events.map { it.content }
                    is TimelineItem.Virtual -> emptyList()
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
        return rendered
    }

    private fun items(vararg contents: EventContent) = contents.mapIndexed { index, content ->
        MatrixTimelineItem.Event(UniqueId("event-$index"), anEventTimelineItem(content = content, timestamp = index.toLong()))
    }
}
