/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aMessageEvent
import io.element.android.features.messages.impl.timeline.components.event.aMediaStackEvent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEmoteContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemNoticeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemPollContent
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import org.junit.Test

class CanDoubleTapToReactTest {
    @Test
    fun `text, emote and notice messages react to a double tap`() {
        assertThat(aMessageEvent().canDoubleTapToReact(canSendReaction = true)).isTrue()
        assertThat(
            aMessageEvent(content = TimelineItemEmoteContent(body = "* Camila waves", htmlDocument = null, formattedBody = "* Camila waves", isEdited = false))
                .canDoubleTapToReact(canSendReaction = true)
        ).isTrue()
        assertThat(
            aMessageEvent(content = TimelineItemNoticeContent(body = "Notice", htmlDocument = null, formattedBody = "Notice", isEdited = false))
                .canDoubleTapToReact(canSendReaction = true)
        ).isTrue()
    }

    @Test
    fun `media, polls and removed messages keep their own taps`() {
        assertThat(aMessageEvent(content = aTimelineItemImageContent()).canDoubleTapToReact(canSendReaction = true)).isFalse()
        assertThat(aMessageEvent(content = aTimelineItemPollContent()).canDoubleTapToReact(canSendReaction = true)).isFalse()
        assertThat(aMessageEvent(content = TimelineItemRedactedContent).canDoubleTapToReact(canSendReaction = true)).isFalse()
    }

    @Test
    fun `a message that failed to send keeps its taps`() {
        assertThat(aMessageEvent(sendState = LocalEventSendState.Failed.Unknown("error")).canDoubleTapToReact(canSendReaction = true)).isFalse()
    }

    @Test
    fun `a media stack reacts to a double tap through its first member`() {
        assertThat(aMediaStackEvent(isMine = true, members = List(2) { aTimelineItemImageContent() }).canDoubleTapToReact(canSendReaction = true)).isTrue()
        val laterMemberFailed = aMediaStackEvent(
            isMine = true,
            members = List(2) { aTimelineItemImageContent() },
            sendStates = listOf(null, LocalEventSendState.Failed.Unknown("error")),
        )
        assertThat(laterMemberFailed.canDoubleTapToReact(canSendReaction = true)).isTrue()
        val firstMemberFailed = aMediaStackEvent(
            isMine = true,
            members = List(2) { aTimelineItemImageContent() },
            sendStates = listOf(LocalEventSendState.Failed.Unknown("error"), null),
        )
        assertThat(firstMemberFailed.canDoubleTapToReact(canSendReaction = true)).isFalse()
    }

    @Test
    fun `nothing happens without permission to react`() {
        assertThat(aMessageEvent().canDoubleTapToReact(canSendReaction = false)).isFalse()
    }
}
