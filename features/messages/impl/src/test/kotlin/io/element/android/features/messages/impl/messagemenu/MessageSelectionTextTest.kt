/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.view.spans.CustomMentionSpan
import org.junit.Test

class MessageSelectionTextTest : RobolectricTest() {
    private val camila = UserId("@camila:gua.global")
    private val rafa = UserId("@rafa:gua.global")

    @Test
    fun `a plain body is kept as it is`() {
        val content = aTextContent(body = "**not markdown here**", formattedBody = "**not markdown here**")

        assertThat(content.selectableText(FakeMentionSpanUpdater())).isEqualTo("**not markdown here**")
    }

    @Test
    fun `mentions are written the way their pills show them`() {
        val formattedBody = SpannableStringBuilder("Oi ")
            .appendMention("Camila", MentionSpan(MentionType.User(camila)))
            .append(", tudo bem?")

        val content = aTextContent(body = "Oi [Camila](https://matrix.to/#/@camila:gua.global), tudo bem?", formattedBody = formattedBody)

        assertThat(content.selectableText(FakeMentionSpanUpdater(mapOf(camila to "@Camila")))).isEqualTo("Oi @Camila, tudo bem?")
    }

    @Test
    fun `a mention without a display name shows the handle, never the full user ID`() {
        val formattedBody = SpannableStringBuilder("Oi ")
            .appendMention("@camila:gua.global", CustomMentionSpan(MentionSpan(MentionType.User(camila)), "https://matrix.to/#/@camila:gua.global"))
            .append(" e ")
            .appendMention("@rafa:gua.global", MentionSpan(MentionType.User(rafa)))

        val content = aTextContent(body = "Oi @camila:gua.global e @rafa:gua.global", formattedBody = formattedBody)

        assertThat(content.selectableText(FakeMentionSpanUpdater())).isEqualTo("Oi @camila e @rafa")
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val content = aTextContent(body = "  Que paz nesse lugar  ", formattedBody = "\n  Que paz nesse lugar  \n")

        assertThat(content.selectableText(FakeMentionSpanUpdater())).isEqualTo("Que paz nesse lugar")
    }

    private fun SpannableStringBuilder.appendMention(text: String, span: Any): SpannableStringBuilder {
        val start = length
        append(text)
        setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    private fun aTextContent(body: String, formattedBody: CharSequence) = TimelineItemTextContent(
        body = body,
        htmlDocument = null,
        formattedBody = formattedBody,
        isEdited = false,
    )
}
