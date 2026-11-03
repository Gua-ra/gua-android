/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import org.junit.Test

class MessageMenuSectionsTest {
    @Test
    fun `an incoming text message`() {
        val sections = MessageMenuSections.from(
            listOf(
                TimelineItemAction.Reply,
                TimelineItemAction.Forward,
                TimelineItemAction.Pin,
                TimelineItemAction.CopyText,
                TimelineItemAction.CopyLink,
                TimelineItemAction.ReportContent,
            )
        )

        assertThat(sections.primary).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.Reply),
            MessageMenuAction.Timeline(TimelineItemAction.CopyText),
            MessageMenuAction.Select,
            MessageMenuAction.Timeline(TimelineItemAction.Forward),
        ).inOrder()
        assertThat(sections.more).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.Pin),
            MessageMenuAction.Timeline(TimelineItemAction.CopyLink),
            MessageMenuAction.Timeline(TimelineItemAction.ReportContent),
        ).inOrder()
        assertThat(sections.destructive).isEmpty()
    }

    @Test
    fun `an outgoing text message`() {
        val sections = MessageMenuSections.from(
            listOf(
                TimelineItemAction.Reply,
                TimelineItemAction.Forward,
                TimelineItemAction.Edit,
                TimelineItemAction.Pin,
                TimelineItemAction.CopyText,
                TimelineItemAction.CopyLink,
                TimelineItemAction.Redact,
            )
        )

        assertThat(sections.primary).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.Reply),
            MessageMenuAction.Timeline(TimelineItemAction.CopyText),
            MessageMenuAction.Select,
            MessageMenuAction.Timeline(TimelineItemAction.Edit),
            MessageMenuAction.Timeline(TimelineItemAction.Forward),
        ).inOrder()
        assertThat(sections.more).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.Pin),
            MessageMenuAction.Timeline(TimelineItemAction.CopyLink),
        ).inOrder()
        assertThat(sections.destructive).containsExactly(MessageMenuAction.Timeline(TimelineItemAction.Redact))
    }

    @Test
    fun `media with a caption has no Select and keeps Remove caption under More`() {
        val sections = MessageMenuSections.from(
            listOf(
                TimelineItemAction.Reply,
                TimelineItemAction.Forward,
                TimelineItemAction.EditCaption,
                TimelineItemAction.CopyCaption,
                TimelineItemAction.RemoveCaption,
                TimelineItemAction.CopyLink,
                TimelineItemAction.Redact,
            )
        )

        assertThat(sections.primary).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.Reply),
            MessageMenuAction.Timeline(TimelineItemAction.CopyCaption),
            MessageMenuAction.Timeline(TimelineItemAction.EditCaption),
            MessageMenuAction.Timeline(TimelineItemAction.Forward),
        ).inOrder()
        assertThat(sections.more.last()).isEqualTo(MessageMenuAction.Timeline(TimelineItemAction.RemoveCaption))
        assertThat(sections.destructive).containsExactly(MessageMenuAction.Timeline(TimelineItemAction.Redact))
    }

    @Test
    fun `view source and threads go under More`() {
        val sections = MessageMenuSections.from(
            listOf(
                TimelineItemAction.ReplyInThread,
                TimelineItemAction.Reply,
                TimelineItemAction.Pin,
                TimelineItemAction.CopyText,
                TimelineItemAction.CopyLink,
                TimelineItemAction.ViewSource,
                TimelineItemAction.ReportContent,
            )
        )

        assertThat(sections.more).containsExactly(
            MessageMenuAction.Timeline(TimelineItemAction.ReplyInThread),
            MessageMenuAction.Timeline(TimelineItemAction.Pin),
            MessageMenuAction.Timeline(TimelineItemAction.CopyLink),
            MessageMenuAction.Timeline(TimelineItemAction.ViewSource),
            MessageMenuAction.Timeline(TimelineItemAction.ReportContent),
        ).inOrder()
    }

    @Test
    fun `a poll can be ended from More`() {
        val sections = MessageMenuSections.from(listOf(TimelineItemAction.EndPoll, TimelineItemAction.EditPoll, TimelineItemAction.Redact))

        assertThat(sections.primary).containsExactly(MessageMenuAction.Timeline(TimelineItemAction.EditPoll))
        assertThat(sections.more).containsExactly(MessageMenuAction.Timeline(TimelineItemAction.EndPoll))
    }

    @Test
    fun `every action has a place except the ones the action list never shows`() {
        val unplaced = TimelineItemAction.entries.filter { MessageMenuAction.Timeline(it).placement == null }

        assertThat(unplaced).containsExactly(TimelineItemAction.RetrySending, TimelineItemAction.AbortSending)
    }

    @Test
    fun `nothing to show`() {
        assertThat(MessageMenuSections.from(emptyList()).isEmpty).isTrue()
        assertThat(MessageMenuSections.from(listOf(TimelineItemAction.ViewSource)).isEmpty).isFalse()
    }
}
