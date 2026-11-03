/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.text.Spanned
import androidx.core.text.getSpans
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.wysiwyg.view.spans.CustomMentionSpan

fun TimelineItemTextBasedContent.selectableText(mentionSpanUpdater: MentionSpanUpdater): String {
    val text = mentionSpanUpdater.updateMentionSpans(formattedBody)
    if (text !is Spanned) return text.toString().trim()
    val mentions = buildList {
        text.getSpans<CustomMentionSpan>().forEach { span ->
            (span.providedSpan as? MentionSpan)?.let { add(MentionRange(text.getSpanStart(span), text.getSpanEnd(span), it)) }
        }
        text.getSpans<MentionSpan>().forEach { span ->
            add(MentionRange(text.getSpanStart(span), text.getSpanEnd(span), span))
        }
    }
    val builder = StringBuilder(text)
    mentions
        .filter { it.start in 0..it.end }
        .distinctBy { it.start }
        .sortedByDescending { it.start }
        .forEach { mention ->
            val original = builder.substring(mention.start, mention.end)
            builder.replace(mention.start, mention.end, mention.span.pillText(original))
        }
    return builder.toString().trim()
}

private data class MentionRange(val start: Int, val end: Int, val span: MentionSpan)

private fun MentionSpan.pillText(original: String): String {
    val type = type
    val displayText = displayText.toString()
    return when {
        // A pill falls back to the full user ID when the member has no display name, which Gua never shows.
        type is MentionType.User && (displayText.isEmpty() || displayText == type.userId.value) -> type.userId.displayHandle
        displayText.isEmpty() -> original
        else -> displayText
    }
}
